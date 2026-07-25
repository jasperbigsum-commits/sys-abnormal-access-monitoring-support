# 通用控制执行、事件质量与审计集成设计

## 状态

待评审。本文以《自建系统异常访问监测与控制建设方案（方案 A）》附录 B 的验收用例为基线；本次修订同时覆盖监听事件输入完整性、Shiro RBAC 审计样例和接入指南矩阵。

## 问题与目标

当前 `DefaultControlActionTrigger` 对全部非 `RECORD` 动作只写入 `SKIPPED`。它能暴露缺失的宿主接入，但不构成实际限流、拒绝、会话撤销或审批。当前 MVC 动作事件又在业务方法完成后记录，因此规则命中后的控制无法阻止触发该事件的本次请求。

同时，`SecurityEventDraft` 目前只校验通用字段的非空、长度和负数值。资源、组织范围、数据量、布尔业务标志和基线比例等规则输入缺失时，可能被当作默认值或被静默忽略；例如未采集的 `dataCount` 会表现为 `0`，但窗口聚合仍可能按一次观测参与统计。参数路径解析失败也不会留下可审计的输入质量结论。这样既可能漏检，也可能用不完整输入触发错误告警或控制。

`integration-audit` 目前只提供固定身份和事件采集样例，没有真实权限框架、资源范围授权器或 `ResourceAccessGuard` 的请求前调用，因此不能验收“授权结论由宿主决定、拒绝会被记录”的关键边界。

本次设计新增三项能力：

1. 一条可选、可真实执行的通用控制路径：对可信来源 IP 的后续请求执行 `RATE_LIMIT`，并可在明确配置时执行 IP 范围的临时 `DENY`。Redis 可用时使用分布式状态；未配置 Redis 时使用有界本地内存状态。其他控制动作继续由宿主实现。
2. 以动作、事件类型和已启用规则为维度的事件输入策略。它在启动期验证静态声明，在运行期验证动态事实的存在性、类型、范围和可信来源；不完整事件可审计，但不会参与依赖缺失字段的规则和控制。
3. 仅用于验收的 Shiro RBAC 审计应用。它显式接入 Shiro `Subject`、内存 Realm、资源范围授权和 `ResourceAccessGuard`，用于证明真实 HTTP 授权路径与监测路径协作，而不把 Shiro 依赖带入通用组件。

该设计同时为后续的 MVC 身份上下文、资源授权记录和事件富化适配保留清晰边界，但不把它们与 IP 控制混为同一个自动拦截器。

## 非目标

- 不自动解析或记录 Bearer Token、Cookie、Session 属性、密码或原始请求体。
- 不通过 URL、请求参数或控制器名称猜测资源 ID、组织范围、用户或审批语义。
- 不把客户端提交的资源范围、数据量、权限或角色声明当作授权或高风险规则的可信事实；这些值必须由服务端解析、查询或计算后补充。
- 不因运行期监测事实缺失而改变宿主业务响应。认证、授权、导出审批等业务前置条件仍必须由宿主在副作用前同步执行。
- 不将审计样例中的测试身份选择机制、内存 Realm 或 Shiro 配置自动装配到生产 Starter。
- 不将 `REQUIRE_CAPTCHA`、`REQUIRE_MFA`、`LOCK`、`REVOKE_SESSION`、`REQUIRE_APPROVAL` 实现为无配置的默认控制。
- 不让规则命中后的控制追溯性撤销已经完成的导出、权限变更或业务事务。
- 不在 Redis 运行时故障时静默改用本地内存，以免集群节点得到不一致的控制结论。

## 事件输入质量与事实契约

### 目标语义

事件质量校验分为四层，避免把“采集问题”误解为“业务拒绝”：

1. **全局模型校验**：保留 `SecurityEventDraft` 的必填、长度、敏感字段和非负数校验；补充规范 IP、事件类型与结果组合、服务端时钟偏差等语义校验。
2. **静态契约校验**：启动时解析 `@MonitorAction`、类型/方法上的 `@MonitorActionAttribute`、参数上的 `@MonitorActionAttribute` 以及程序化动作注册。动作编码、事件类型、静态属性、规则标记、参数路径、事实名称和声明类型不合法时应启动失败。
3. **动态事实校验**：在参数绑定、方法执行、异常处理和富化完成后，对适用的动态事实检查“是否提供、类型是否正确、取值范围、枚举值、最小可信来源”。
4. **规则资格校验**：每个规则只接收满足其输入契约的事件。缺失或无效的事实不会被赋予业务默认值，不会参与相关聚合，也不会产生相关控制指令。

默认运行策略为 `RECORD_INCOMPLETE_AND_SKIP_RULES`：仍保存原始的、已脱敏事件和稳定问题码，向输入质量报告器发出诊断，但跳过依赖问题字段的规则。该策略不得向 HTTP 调用方抛出监测异常，也不得覆盖宿主已经作出的认证或授权结论。仅测试和离线校验可启用严格拒绝记录模式，用于尽早发现漏配；它同样不能成为业务授权机制。

### 核心模型与边界

新增框架无关的输入策略接口，候选命名如下：

- `MonitoringEventPolicy`：按动作、事件类型、结果、静态规则标记和启用规则解析适用契约。
- `EventFactRequirement`：描述事实名称、目标字段、是否必填、数据类型、值约束、适用条件和最低可信来源。
- `EventFactType`：至少覆盖 `TEXT`、`IP_ADDRESS`、`BOOLEAN`、`NON_NEGATIVE_LONG`、`DECIMAL`、`ENUM` 和 `RESOURCE_REFERENCE`。
- `EventFactSource`：区分 `STATIC_DECLARATION`、`TRUSTED_REQUEST`、`TRUSTED_IDENTITY`、`METHOD_PARAMETER`、`SERVER_COMPUTED`、`AUTHORIZATION` 和 `EVENT_ENRICHER`。
- `EventInputValidation`：给出 `VALID`、`INCOMPLETE` 或 `INVALID`、稳定问题码以及每条规则的资格结论；问题码不得携带原始参数值。
- `MonitoringInputIssueReporter`：可选端口，用于指标、审计或告警；默认实现只输出受限的动作、事件类型、规则 ID、问题码和来源类别。

`SecurityEventDraft` 必须能区分“未提供的数量”和“值为零”。实现上可将内部数量改为可选值并保留兼容访问器，同时新增 `hasDataCount()`、`hasLatencyMs()` 或等价事实存在标记。`WindowAggregateRule.Aggregation.DATA_COUNT` 只能消费明确提供且通过校验的数据量，绝不能将缺失数量按一次事件或零条数据处理。

输入质量结论应作为安全事件的显式、可持久化审计元数据保存，例如 `inputStatus` 与稳定的 `inputIssueCodes`，而不是使用可由动态属性覆盖的普通键。为支持查询和审计，相关领域模型、MyBatis 映射及建表脚本需要同时升级；迁移前历史事件按 `UNKNOWN` 处理。

### 可信字段与覆盖规则

`ActionEventRecorder` 需要从“暴露原始草稿构建器”收敛为受限事实补充入口。动作定义、事件类型、来源、请求/追踪标识、身份、角色、会话散列、服务端发生时间、静态属性和规则标记由系统锁定；调用方只能写入已声明的动态事实。保留原始 `SecurityEventDraft` 入口时，必须显式命名为宿主可信入口并在文档中说明其责任，不能让注解路径间接使用它。

事实发生冲突时按可信来源优先级合并：`AUTHORIZATION` / `SERVER_COMPUTED` / `TRUSTED_*` 高于 `METHOD_PARAMETER`，而 `STATIC_DECLARATION` 和保留框架键不可被任何动态来源覆盖。属性键在进入定义、绑定和合并前统一规范化为小写稳定形式；同一规范化键的静态定义重复应启动失败，动态低可信键不得利用大小写变体覆盖 `sensitivity` 或 `monitor.rule-tag.*`。

`EventEnricher` 不能继续以可替换整个草稿的方式接入。新编排器应把它收敛为只读上下文加受限 `EventFactContribution`，或在兼容期逐项验证返回草稿的受保护字段完全相等。无论采用何种兼容方案，所有 Enricher 都必须在统一编排器中执行，随后立即进行输入策略验证；不能由宿主手工遗漏调用。

### 静态与动态参数采集矩阵

下表是后续《集成指南》必须给出的正式矩阵；“可信级别”决定某个来源能否满足高风险规则或授权所需事实。

| 采集位置 | 阶段 | 可提供的事实 | 可信级别 | 契约与限制 |
| --- | --- | --- | --- | --- |
| `@MonitorAction` | 启动期 | 动作编码、事件类型、资源类型、规则标记 | `STATIC_DECLARATION` | 必填静态元数据；冲突和非法编码启动失败。 |
| 类型/方法 `@MonitorActionAttribute` | 启动期 | 固定非敏感属性，例如 `sensitivity=HIGH` | `STATIC_DECLARATION` | 只能声明 `ATTRIBUTE`；键和值必须合法，不能使用保留前缀。 |
| 参数 `@MonitorActionAttribute` | 方法执行前 | `RESOURCE_ID`、`ORG_SCOPE`、非敏感 `ATTRIBUTE` | `METHOD_PARAMETER` | 支持空路径和 Java Bean 相对路径，如 `report.id`、`tenant.code`；路径只允许公开 getter/字段、数组或列表下标，拒绝 `class`、静态成员和不安全键。参数值通常只能满足低可信采集要求。 |
| `MonitorActionEnricher` | 方法前、返回后或异常后 | 服务端计算的数据量、时延、最终结果、稳定原因码、已解析资源摘要 | `SERVER_COMPUTED` | 只补充声明过的事实；可用于满足导出数据量、服务端资源范围等高风险输入。 |
| `IdentityContextProvider` 与请求适配器 | 请求进入时 | 规范来源 IP、请求/追踪 ID、主体、角色、会话散列 | `TRUSTED_REQUEST` / `TRUSTED_IDENTITY` | 仅从已认证上下文或可信代理规则获取；不采集原始 Token、Cookie、Session 属性或请求体。HTTP 缺少合法 IP 时不得参与 IP 规则；任务/消息需显式非 HTTP 来源类型。 |
| `ResourceAccessGuard` | 资源事实确定且副作用前 | 资源类型、资源 ID、组织范围、允许/拒绝、原因码 | `AUTHORIZATION` | 宿主显式调用；授权事实来自服务端资源解析和授权器，不能由 URI 或客户端参数推断。 |
| `EventEnricher` | 标准记录流水线的最终富化阶段 | 跨入口的批准摘要 | `EVENT_ENRICHER` | 只能添加未锁定的事实；统一编排、逐项保护受信任字段、随后运行输入策略。 |
| `ActionEventRecorder` 程序化 API | 服务、任务或消息处理完成后 | 已注册动作的动态事实 | 由调用方声明 | 仅已注册动作可用；调用方要选择事实来源类别，不能伪装为授权或身份事实。 |

### 基线规则最低输入矩阵

| 场景 / 规则 | 运行时最低事实 | 无效或缺失时的处理 |
| --- | --- | --- |
| 登录失败 `AUTH-01` | 合法可信来源、`FAILURE` 或 `DENIED` 结果；账号维度分析还需单向散列后的尝试账号 | 事件可记录为不完整；不得进入 IP/账号失败聚合。 |
| 多账号同 IP `AUTH-02` | 合法 IP、稳定且已脱敏的账号主体 | 跳过 IP 聚合与后续 IP 控制。 |
| 资源拒绝 `AUTHZ-01` | `DENIED` 结果、服务端资源类型/ID/组织范围 | 仅由 `ResourceAccessGuard` 或等价授权路径产生；缺失时跳过资源范围规则。 |
| 资源遍历 `AUTHZ-02`、高频查询 `DATA-02` | 稳定主体或会话、资源 ID、严格布尔 `sequential_access`（适用时） | 不参与去重资源聚合。 |
| 非工作时敏感读取 `DATA-03` | 锁定静态 `sensitive=true`、严格布尔 `work_hours=false`、显式 `dataCount`、稳定主体 | 不将缺失数量按一次计数；跳过数据量聚合。 |
| 导出 `EXPT-01` / `EXPT-02` | 服务端资源 ID、显式数据量、有限且非负的 `baseline_ratio`（适用时）、受控敏感级别 | 记录输入质量问题，跳过导出阈值、累计和基线规则。 |
| 角色授予 `PRIV-01` | 服务端操作者、目标用户摘要、严格布尔 `privilege_increase` / `high_privilege` | 不触发权限升级控制或高危告警。 |
| 安全配置变更 `SECU-01` | 服务端主体、稳定原因码、版本或变更编号摘要 | 记录不完整事件，不满足配置审计规则。 |

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

控制状态写入必须与控制记录使用同一幂等键。活动控制期间的重复投递不延长有效期；跨过期、重启和节点的长期幂等由 `DefaultControlService` 的持久化记录负责，本地有界状态会释放过期键。需要并发原子领取或多实例强一致的宿主必须提供相应仓储或分布式处理器。

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

## Shiro RBAC 审计集成

### 隔离与版本边界

Shiro 仅作为验收依赖放在 `integration-audit/spring2-web` 与 `integration-audit/spring3-web`。`api`、`core`、`spring-support`、两个 Starter、BOM 和业务接入 API 都不新增 Shiro 类型或传递依赖，保证组件继续可接入 Spring Security、自建认证、无 Web 的任务和其他权限框架。

`integration-audit/spring2-web` 保持现有 Java 8 / `javax.servlet` 基线，并使用 `org.apache.shiro:shiro-spring-boot-web-starter:1.13.0`。`integration-audit/spring3-web` 保持 Java 17 / `jakarta.servlet`，在模块自己的 `dependencyManagement` 导入 `org.apache.shiro:shiro-bom:2.0.4`，再由该 BOM 解析 Jakarta 变体的 `shiro-spring-boot-web-starter`。Shiro 2 的无 classifier 依赖仍是 `javax`，因此 Boot 3 不能只增加单个 starter 或手工混用 `javax`/`jakarta` JAR。实现时必须通过 Maven dependency tree 和两个应用上下文启动测试验证 Servlet API 的解析，不能把任何一个示例依赖提升到公共依赖管理。

Shiro 1.13.0 仅保留给 Boot 2 的遗留 `javax` 验收夹具；正式宿主系统应优先迁移到 Boot 3/Jakarta 和 Shiro 2.x。当前推荐方案以“可运行的双版本验收”与“核心模块 Java 8 兼容”之间的隔离为准。

### 验收架构

每个审计样例新增独立的 `AuditShiroRbacConfiguration`，包含：

- 内存 `Realm`，定义仅用于验收的主体、角色、权限和组织范围映射，例如 `audit-admin`、`audit-viewer` 和无导出权限主体；示例中不存放密码、Token、Cookie 或生产身份数据。
- 显式、拒绝优先的 `ShiroFilterChainDefinition`。不能依赖 Shiro 2 的默认放行行为；每个 `/audit/**` 业务路径都必须列出认证与权限规则，未声明路径统一拒绝。
- 仅位于 `integration-audit` 的测试主体 Filter。它把受控的 `X-Audit-Principal` 固定测试值绑定为 Shiro `Subject`；缺失或未知值返回 401。该 Header 只是无密钥的测试身份选择器，绝不能复制到 Starter、业务应用或作为正式生产接入方案；接入指南只能将其标注为验收夹具。
- `IdentityContextProvider` 的 Shiro 适配器，只从已认证 `Subject` 读取主体、受控角色和可选会话散列。它不读取原始 Header、凭据、Cookie、Token 或 Session 属性。
- `ResourceScopeAuthorizer` 的 Shiro 适配器，同时检查 `Subject.isPermitted(...)` 和服务器保存的“主体到组织范围”映射。控制器先从服务端资源目录解析资源 ID、组织范围和导出行数，再在副作用前调用 `ResourceAccessGuard.authorize(...)`；客户端请求体、URL 和注解参数不能直接成为授权结论。

审计示例的 `@MonitorActionAttribute(path = "report.id")` 与 `path = "tenant.code"` 继续展示 Java Bean 路径采集，但它们只产生 `METHOD_PARAMETER` 级别的监测候选事实。实际授权和高风险导出规则由 `AuditReportCatalog` 或等价服务端解析结果以 `SERVER_COMPUTED` / `AUTHORIZATION` 级别补充，避免伪造请求体中的组织范围绕过授权或污染监测。

### Shiro RBAC 验收用例

| 用例 | 预期 HTTP / 授权结果 | 预期监测结果 |
| --- | --- | --- |
| 未指定测试主体访问受保护资源 | 401；不构造伪造身份 | 不产生以认证主体冒充的业务访问事件。 |
| `audit-viewer` 读取已授权组织的报告 | 200；`report:read` 和组织范围同时满足 | 记录 `ACCESS_ALLOWED`，身份和角色来自 Shiro Subject。 |
| `audit-viewer` 导出或访问其他组织 | 403；控制器在导出前由 `ResourceAccessGuard` 拒绝 | 记录 `RESOURCE_SCOPE_DENIED` 和稳定原因码；监测写入失败不得把拒绝改为允许。 |
| `audit-admin` 导出已授权报告 | 200；`report:export` 与组织范围满足 | 静态敏感级别、参数路径资源 ID、服务端实际行数和最终结果共同形成完整 `EXPORT` 事件。 |
| 缺失/非法 Bean 路径、缺失服务端行数或非法布尔/比例 | 业务授权结果不变 | 记录输入质量问题，相关规则和控制不执行。 |
| 动态属性使用大小写变体覆盖静态属性或规则标记 | 启动失败或动态贡献被拒绝 | 静态 `sensitivity` 与 `monitor.rule-tag.*` 保持不可覆盖。 |

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

- api/core 单元测试：全局字段与语义校验、事实存在标记、事件/结果组合、IP 与时钟偏差、静态键规范化、规则资格和无敏感值的问题码。
- spring-support 单元测试：参数空路径、Bean 路径、列表下标、非法路径、getter 异常、类型不匹配和动态大小写覆盖；参数采集失败必须形成可断言的事实缺失，而不是静默成为完整事件。
- core 单元测试：控制状态 TTL、幂等、规则 ID 白名单和不支持主体拒绝执行；未提供的 `dataCount` 不得参与 `DATA_COUNT` 聚合；不完整事件不得产生依赖事实的规则命中或控制。
- Boot 2 与 Boot 3 集成测试：激活 IP 控制后，同 IP 后续请求收到 429 或 403；其他 IP、排除路径和 `OBSERVE` 模式不受影响。
- Boot 2 与 Boot 3 输入质量集成测试：注解静态定义非法时应用启动失败；缺失、空白、错误类型、非法枚举、非法 IP、过期/未来时间和 Enricher 失败均形成稳定诊断，且不改变业务响应。
- 两个 `integration-audit` Shiro RBAC 集成测试：应用上下文和拒绝优先 Filter 链生效；未认证 401、同组织读取 200、跨组织/无导出权限 403、`ResourceAccessGuard` 记录拒绝、服务端行数和参数路径被采集；监测失败不能绕过授权。
- 存储测试：本地容量/过期；Redis 原子计数和 TTL 使用独立集成环境验证。
- 安全测试：不信任伪造转发头；响应不泄露规则信息；Token、Cookie、密码、原始 Session、测试主体 Header 和原始请求体不进入控制状态、事件、诊断或日志。
- 宿主验收：以方案 A 的 TC-01 至 TC-18 为基线，将“规则检测”“通用后续拦截”“宿主事前控制”分别断言，禁止把模拟 Handler 调用当成实际阻断验收。

## 接入指南交付物

实现完成后更新 `docs/集成指南.md`，并同步校对生成模板和审计样例，至少包含以下内容：

1. “监听事件输入质量策略”章节：默认 `RECORD_INCOMPLETE_AND_SKIP_RULES` 行为、问题码不含原始值、哪些情况只影响监测、哪些业务前置条件必须由宿主同步拒绝。
2. 上文“静态与动态参数采集矩阵”的正式版：对每个事实写明采集位置、`@MonitorActionAttribute` 的路径语法、可信级别、可覆盖性和执行阶段；明确参数绑定并不天然等于服务器授权事实。
3. “规则最低输入矩阵”：按动作事件、启用规则和控制类型列出必填事实、数据类型、来源和缺失后的资格处理；对 `dataCount=0` 与未提供作出明确区分。
4. “Shiro RBAC 审计样例”章节：声明它仅是 `integration-audit` 验收工程，给出 Realm、Subject 到 `IdentityContextProvider`、`ResourceScopeAuthorizer`、`ResourceAccessGuard` 的接入顺序和用例命令；不把测试主体 Header 当作生产认证方案。
5. “控制执行矩阵”章节：保留宿主必须接入、Starter 自动后续拦截、仅检测/人工审核三类动作的标识，并链接方案 A 的 TC-01 至 TC-18。

## 实施顺序

1. 为事件模型增加事实存在语义、输入质量结论和持久化迁移；先以测试锁定“缺失不等于默认值”。
2. 实现 `MonitoringEventPolicy`、静态定义启动校验、受限动态事实补充、统一 Enricher 编排和规则资格过滤。
3. 在两个 Starter 中接入输入策略的请求/注解路径，并补足 Servlet 专用身份上下文、显式资源授权适配器和 `EventEnricher` 调用编排。
4. 实现隔离的 Shiro RBAC 审计样例与 Boot 2/3 验收测试，证明真实授权拒绝和事件质量策略协同工作。
5. 增加 core 控制状态端口、规则 ID 传递与状态幂等测试。
6. 实现本地内存状态存储和 Boot 2/3 IP 控制 Handler、Filter 与配置，再增加可选 Redis 存储、健康状态和故障策略。
7. 更新接入指南、生成模板和方案 A 验收矩阵，运行完整 Maven Reactor 验证。
