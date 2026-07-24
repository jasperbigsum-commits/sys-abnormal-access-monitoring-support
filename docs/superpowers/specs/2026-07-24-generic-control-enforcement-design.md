# 通用控制执行与宿主适配设计

## 状态

待评审。本文以《自建系统异常访问监测与控制建设方案（方案 A）》附录 B 的验收用例为基线。

## 问题与目标

当前 `DefaultControlActionTrigger` 对全部非 `RECORD` 动作只写入 `SKIPPED`。它能暴露缺失的宿主接入，但不构成实际限流、拒绝、会话撤销或审批。当前 MVC 动作事件又在业务方法完成后记录，因此规则命中后的控制无法阻止触发该事件的本次请求。

本次新增一条可选、可真实执行的通用控制路径：对可信来源 IP 的后续请求执行 `RATE_LIMIT`，并可在明确配置时执行 IP 范围的临时 `DENY`。Redis 可用时使用分布式状态；未配置 Redis 时使用有界本地内存状态。其他控制动作继续由宿主实现。

该设计同时为后续的 MVC 身份上下文、资源授权记录和事件富化适配保留清晰边界，但不把它们与 IP 控制混为同一个自动拦截器。

## 非目标

- 不自动解析或记录 Bearer Token、Cookie、Session 属性、密码或原始请求体。
- 不通过 URL、请求参数或控制器名称猜测资源 ID、组织范围、用户或审批语义。
- 不将 `REQUIRE_CAPTCHA`、`REQUIRE_MFA`、`LOCK`、`REVOKE_SESSION`、`REQUIRE_APPROVAL` 实现为无配置的默认控制。
- 不让规则命中后的控制追溯性撤销已经完成的导出、权限变更或业务事务。
- 不在 Redis 运行时故障时静默改用本地内存，以免集群节点得到不一致的控制结论。

## 控制分层

控制处理器按以下优先级解析：

1. 宿主 `ControlHandler` 或 `@ControlTrigger`。
2. 显式启用的 Starter 通用 IP 控制处理器。
3. `DefaultControlActionTrigger` 审计回退，返回 `SKIPPED`。

宿主处理器始终可以替代通用处理器。审计回退保留现有行为，且不能使 `ENFORCE` 启动。通用 IP 控制处理器只有在宿主显式启用、配置了请求过滤器并通过启动校验后才是可执行处理器。

`DefaultControlActionTrigger` 的职责保持为“未实现动作的可审计回退”，不再被描述为真实控制实现。实际默认能力由新的 Starter 控制处理器提供，避免把框架无关的 core 与 Servlet、Redis 或 Boot 版本耦合。

## 可通用的实际控制

### IP 限流

通用 `RATE_LIMIT` 仅接受 `subject` 为 `ip:<canonical-ip>` 的控制指令。它激活一个带 TTL 的限流策略；同一 IP 的后续受保护请求由 Servlet Filter 原子计数并决定放行或返回 HTTP 429。响应包含 `Retry-After`，但不得泄露规则 ID、阈值、告警 ID 或内部原因。

限流策略必须由显式 Starter 配置提供，包括：

- 应用的规则 ID 白名单；默认不对所有 `RATE_LIMIT` 动作生效。
- 请求路径范围；默认不向所有 URL 注入控制。
- 每个时间窗口的许可数、窗口长度和控制 TTL 上限。
- 健康检查、验证码或人工解锁路径的排除规则。

规则命中发生在事件记录后，因此首次命中的请求仍会完成；Filter 只控制后续请求。若业务要求在本次认证失败、导出或权限变更前阻断，必须接入对应的认证、导出预检或授权 SPI。

第一阶段拒绝把账号、用户或会话主体自动转换为 IP。它们需要在认证完成后的可靠身份上下文中执行，属于后续 Servlet 身份 SPI 和宿主策略适配范围。

### IP 临时拒绝

可选的 `DENY` 仅在规则 ID 和 `ip:` 主体同时被显式允许时生效。Filter 对匹配请求返回 HTTP 403。它不能默认承接所有 `DENY` 动作，避免把停用账号、导出审批或资源授权类规则错误扩大为整段来源 IP 封禁。

## 必须由宿主实现的动作

| 动作 | 原因 |
| --- | --- |
| `REQUIRE_CAPTCHA` | 验证码供应商、登录字段、挑战放行和失败升级规则均属于宿主认证链。 |
| `REQUIRE_MFA` | MFA 会话状态、挑战协议和完成判断属于宿主身份系统。 |
| `LOCK` | 账号/IP 锁定策略、管理员解锁和拒绝服务防护属于宿主。 |
| `REVOKE_SESSION` | Servlet Session、Spring Security、JWT 黑名单和自建会话服务没有统一失效协议。 |
| `REQUIRE_APPROVAL` | 审批对象、职责分离和业务恢复点不能由通用组件推断。 |
| 非 IP 的 `DENY` | 用户、会话、资源和导出对象的授权结论必须在宿主业务或授权层作出。 |

## 架构

### Core

core 新增框架无关的控制状态端口和领域值对象。端口负责：激活控制、按规范化 IP 查询有效控制、在过期时清理、保持控制幂等键。它不依赖 Servlet、Spring 或 Redis。

为使策略能够按规则安全选择，`ControlCommand` 增加可选 `ruleId`，并保留原构造器以维持兼容。`DefaultSecurityMonitor` 在创建控制指令时填入 `RuleMatch` 的规则 ID。通用处理器只接受配置允许的 `ruleId + action + subject-kind` 组合。

控制状态写入必须与控制记录使用同一幂等键。重复投递不延长有效期，除非宿主显式选择新的控制指令。

### Spring Boot 2 与 Spring Boot 3 Starter

两个 Starter 分别实现 Servlet 层，使用各自的 `javax.servlet` 与 `jakarta.servlet` 命名空间：

- 可选通用 IP 控制 `ControlHandler`。
- `OncePerRequestFilter`，在 MVC 处理器之前读取有效控制状态。
- 共享可信代理解析规则，不能直接信任任意 `X-Forwarded-For`。
- 自动配置与属性校验；未显式启用时不注册实际阻断能力。

Filter 不能依赖 MVC `HandlerInterceptor` 已写入的请求属性，因为 Filter 更早执行。IP 解析逻辑需要复用同一可信代理策略；身份、会话和业务资源等更晚才可获得的事实不属于第一阶段的通用 IP Filter。

### Redis 与内存存储

Redis 支持是可选依赖，必须放入独立的 `@ConditionalOnClass` 自动配置中。仅当运行时存在可用的 `StringRedisTemplate` 或等价 Redis Bean 时，Starter 选择 Redis 实现；否则选择有最大容量、TTL 和惰性过期清理的本地内存实现。

Redis 的激活、TTL 写入和请求计数必须使用 Lua 或等价的单次原子操作。内存实现只保证单 JVM 语义，Starter 必须通过健康状态、日志和文档标明 `LOCAL_FALLBACK`，不能声称跨节点限流有效。

Redis 运行时不可用时默认不切换存储实现，并遵循可配置的失败策略。默认 `FAIL_OPEN` 只意味着该通用限流暂时不生效，绝不绕过宿主既有认证、授权或资源访问控制；同时必须暴露高优先级健康信号。`FAIL_CLOSED` 仅允许宿主显式启用。

## 宿主适配边界

`IdentityContextProvider` 继续保持框架无关。后续在两个 Starter 各提供带原始 `HttpServletRequest` 的专用身份 SPI，供宿主从已认证 SecurityContext、会话或自建认证服务构造 `IdentityContext`。它不自动读取或保存原始 Token。

`ResourceAccessGuard` 不能在所有 MVC 请求上自动调用，因为 `resourceType`、`resourceId` 和 `orgScope` 是业务语义。宿主应在资源已知、业务副作用发生前调用它；后续可增加要求显式资源映射的 MVC 注解或适配器。

`EventEnricher` 需要一个由标准记录入口显式调用的编排器。它与 `MonitorActionEnricher` 分离：前者补充已批准的事件摘要，后者只服务于一个已标注方法调用。两者均不能覆盖身份、IP、结果或授权事实。

## 验收映射

| 用例 | 本次通用能力 | 宿主前置条件 |
| --- | --- | --- |
| TC-01 | 仅保留规则检测；不将账号主体自动转换为 IP 限流 | 登录挑战、账号维度策略和失败计数适配 |
| TC-02 | 完整支持 IP 限流 | 可信代理配置与显式规则启用 |
| TC-03 | 不支持默认拒绝 | 认证前停用账号校验 |
| TC-04、TC-05 | 不支持默认授权 | 显式 `ResourceAccessGuard` 调用与资源事实 |
| TC-06 | 仅保留规则检测；不撤销会话 | 会话撤销和主体范围适配器 |
| TC-07 | 仅保留规则检测；用户主体限流属于后续身份适配阶段 | 查询事件、身份上下文和路径范围配置 |
| TC-08、TC-09 | 不支持当次导出阻断 | 导出预检、审批与业务数据量事实 |
| TC-10 | 不支持默认拒绝 | 权限变更事务前授权 |
| TC-11 | 不支持默认会话撤销 | 宿主会话失效实现 |
| TC-12 | 现有规则和仓储覆盖 | 白名单管理权限由宿主提供 |
| TC-13 | 扩展后必须覆盖状态与控制记录幂等 | 无 |
| TC-14 | 不属于 Trigger | 通知有限重试机制 |
| TC-15 | 保持现有敏感字段禁令 | 宿主端到端日志验收 |
| TC-16 | 保持规则事件记录 | 管理权限、审批和版本审计 |
| TC-17 | Filter 在 `OBSERVE` 时不拦截 | 可运行期切换的宿主配置源 |
| TC-18 | 保持现有告警生命周期 | 管理端权限控制 |

## 测试策略

- core 单元测试：控制状态 TTL、幂等、规则 ID 白名单和不支持主体拒绝执行。
- Boot 2 与 Boot 3 集成测试：激活 IP 控制后，同 IP 后续请求收到 429 或 403；其他 IP、排除路径和 `OBSERVE` 模式不受影响。
- 存储测试：本地容量/过期；Redis 原子计数和 TTL 使用独立集成环境验证。
- 安全测试：不信任伪造转发头；响应不泄露规则信息；Token、Cookie 和原始 Session 不进入控制状态、事件或日志。
- 宿主验收：以方案 A 的 TC-01 至 TC-18 为基线，将“规则检测”“通用后续拦截”“宿主事前控制”分别断言，禁止把模拟 Handler 调用当成实际阻断验收。

## 实施顺序

1. 增加 core 控制状态端口、规则 ID 传递与状态幂等测试。
2. 实现本地内存状态存储和 Boot 2/3 IP 控制 Handler、Filter 与配置。
3. 增加可选 Redis 存储、健康状态和故障策略。
4. 更新接入指南、生成模板和方案 A 验收矩阵。
5. 单独设计并实施 MVC 身份上下文、资源授权适配和 `EventEnricher` 编排器。
