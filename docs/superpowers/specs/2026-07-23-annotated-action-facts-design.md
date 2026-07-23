# 注解动作事实采集设计

## 目标

完善接入指南中的动作编码支持矩阵，并让 Servlet MVC 的 `@MonitorAction` 能在不影响宿主请求的前提下采集两类当前无法取得的事实：

- 调用前可从控制器方法参数得到的资源标识、组织范围和已脱敏属性；
- 调用完成后才能得到的导出条数、业务结果、原因码、耗时和其他已批准事实。

该能力必须同时支持 Spring Boot 2 和 Spring Boot 3，并保持 `api`、`core` 与 Spring Starter 的既有依赖边界。

## 当前缺口

现有 MVC `HandlerInterceptor` 只在 `preHandle` 保存 `MonitorAction`，并在 `afterCompletion` 依据 HTTP 状态调用 `ActionEventRecorder.record(...)`。`afterCompletion` 不提供控制器方法参数和返回值；因此注解动作只能写入静态定义、可信请求上下文、身份和 HTTP 级结果。动态资源 ID、数据量、业务原因码和执行期属性只能改用注册式调用。

此外，内置 `AUTH-02` 规则使用 `SecurityEvent.subject()` 统计同一 IP 的不同登录主体。匿名登录失败事件的 subject 会退化为来源 IP，导致该规则无法可靠识别同 IP 对多个账户的失败尝试。

## 方案选择

不采用 SpEL/属性路径表达式：它把 Java 参数名、DTO 结构和安全语义变成运行时字符串，重构时容易静默失效。

不采用注解指向静态反射方法：它没有依赖注入边界，难以测试，且会把业务对象和敏感数据处理藏在不可审计的反射调用中。

采用“独立静态属性注解 + 类型化调用事实采集器”方案：静态事实属于动作定义；动态事实由显式 Spring Bean 在调用前后提取，输出受限的事实对象而不是可覆盖任意可信字段的事件草稿。

## API 设计

### 静态属性

新增可重复使用的 `@MonitorActionAttribute`，目标为类型和方法。它包含 `key` 与 `value`，表示与动作编码稳定绑定的非敏感属性，例如固定的 `sensitivity=HIGH` 或 `high_privilege=true`。

动作解析遵循既有优先级：方法上的 `@MonitorAction` 优先于类型上的注解；静态属性也只从最终选中的声明元素读取，不合并方法与类型的两个动作定义。这样不会让类型默认动作意外继承到具有独立方法动作的场景。

`MonitorActionDefinition` 新增不可变静态属性集合，Builder 允许注册式入口提供同样的属性；`ActionEventRecorder.draft(...)` 在创建草稿时写入这些属性。规则标签仍保持 `monitor.rule-tag.*=true` 的专用语义，不与通用静态属性混用。

### 动态事实

在 `api` 模块新增三个框架无关类型：

- `MonitorActionInvocation`：只读调用快照，包含最终动作定义、反射方法、参数数组、返回值或异常以及采集阶段；
- `MonitorActionEnricher`：由宿主实现的接口，根据调用快照返回动态事实；
- `MonitorActionFacts`：受限、不可变的返回对象，只允许 `resourceId`、`orgScope`、`dataCount`、`latencyMs`、`result`、`reasonCode` 和已清洗的属性。

`@MonitorAction` 新增可选的 `enrichers` 类型数组。每个类型必须对应一个 Spring Bean，因此采集器可以使用宿主的业务服务，但 API 本身不依赖 Spring。采集器分别在调用前、正常返回后和抛出异常后接收快照；多个阶段和多个采集器的事实按确定顺序合并，后者覆盖同一动态字段。

采集器不能写入事件类别、动作编码、来源 IP、请求 ID、追踪 ID、身份、角色、会话或事件时间。属性仍通过 `SecurityFieldSanitizer` 清洗；宿主不得输出密码、令牌、Cookie、原始请求/响应体或可逆账户标识。

## Spring MVC 执行模型

保留现有注解拦截器，新增仅处理已声明 `enrichers` 的 MVC 控制器环绕组件。

1. `preHandle` 解析最终动作定义，建立可信请求和身份上下文，并在 `HttpServletRequest` 中创建请求私有的动作事实容器。
2. 环绕组件在控制器执行前后读取方法参数、返回值、异常和耗时，调用已配置的 `MonitorActionEnricher`，把 `MonitorActionFacts` 合并到该容器。
3. `afterCompletion` 读取容器，用 `ActionEventRecorder.draft(...)` 组装静态定义、可信上下文和动态事实，再记录单个事件。

最终 HTTP 语义优先级如下：

1. 控制器异常产生 `FAILURE/HANDLER_EXCEPTION`；
2. HTTP 401/403 产生 `DENIED/HTTP_<status>`；
3. 其他 HTTP 4xx/5xx 产生 `FAILURE/HTTP_<status>`；
4. 仅当 HTTP 结果为成功时，采集器可提供业务 `result` 与 `reasonCode`；未提供时保持现有 `SUCCESS` 语义。

采集器抛出的异常只放弃该采集器的事实，不改变控制器执行或最终响应。异步 `Callable`、`DeferredResult`、流式响应、非 MVC 框架、Service 自调用、消息消费和定时任务不在本次自动采集范围内，继续使用 `ActionEventRecorder.draft(...)`。

## 规则与动作矩阵

接入指南新增“动作编码支持矩阵”，按动作族展示建议的稳定编码、`SecurityEventType`、内置规则、注解自动采集状态、规则所需额外事实和推荐采集阶段。状态含义为：

- 完整：当前静态注解与可信上下文足以产出事件并满足内置规则的字段需求；
- 需补充：可自动记录动作，但内置规则须由 `MonitorActionEnricher` 或注册式埋点提供额外事实；
- 手工入口：当前 MVC 注解链路不适用，应使用注册式草稿。

矩阵覆盖认证、会话、授权、数据访问/导出、权限变更和安全配置变更。示例将演示：

- `auth:login-failure` 使用 `attempted_account_hash`、`account_status`；
- `session:concurrent` 使用 `dataCount`、`different_networks`；
- `report:export` 使用 `resourceId`、`dataCount`、`sensitivity`、`baseline_ratio`；
- `role:grant` 使用 `target_user_id`、`privilege_increase`、`high_privilege`。

`AUTH-01` 和 `AUTH-02` 改为优先使用 `attempted_account_hash` 归并匿名登录失败；仅当该字段缺失时才保持既有的认证身份或来源 IP 回退策略。该值必须是不可逆哈希，不能是账户原文。

## 测试与兼容性

- API 测试验证静态属性进入动作定义、动态事实字段边界和清洗行为；
- Core 测试验证注册式与注解式定义均保留静态属性，以及匿名登录失败可通过 `attempted_account_hash` 触发 `AUTH-01`/`AUTH-02`；
- Spring 2、Spring 3 各增加等价 MVC 集成测试，验证参数、返回值和业务结果被采集，HTTP 异常/拒绝覆盖采集器业务结果，采集器失败不影响宿主响应；
- 接入指南的矩阵和示例作为宿主验收标准，明确异步与非 MVC 的手工入口。

不修改持久化结构：补充事实继续写入现有事件属性和标准事件字段。
