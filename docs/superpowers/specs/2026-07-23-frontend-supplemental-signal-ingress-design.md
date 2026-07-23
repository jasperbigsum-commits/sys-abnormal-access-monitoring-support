# 前端补充信息统一接入设计

## 背景与目标

浏览器上报的数据只能作为异常访问监测的补充证据，不能取代后端认证、授权、来源地址、会话或风险结论。当前仓库已经提供 `FrontendSignal`、JSON Schema、`FrontendServerContext` 与 `FrontendSignalRecorder`，但集成指南未把 HTTP 接入边界、跨系统字段适配和响应责任说清楚。

本次目标是定义统一的前端补充信息接入功能：所有宿主系统都将前端请求归一化为 `FrontendSignal v1`，经后端校验与可信上下文合并后再记录。组件不定义 Controller 路径、认证方案或成功/失败响应 JSON，避免破坏宿主已有的 API 响应模型。

## 方案选择

采用“统一语义契约，宿主负责 HTTP 适配”的方案。

- HTTP 请求体的标准形式为 `web-contract/src/main/resources/frontend-signal.schema.json` 定义的 `FrontendSignal v1`。
- 每个宿主系统暴露自己的 `POST` 端点，路径由其路由规范、网关和版本策略决定。文档给出示例路径，不将其声明为组件 API。
- 宿主 Controller 将反序列化后的标准信号交给统一记录入口 `FrontendSignalRecorder.record(signal, serverContext)`；该入口是组件的统一接入功能。
- 宿主可以在 Controller 前或 DTO 映射层把既有前端事件适配成标准信号。组件不接收、解析或存储系统特有的原始业务 DTO。
- 成功和失败的响应体完全沿用宿主的统一 API 返回模型。文档只定义 HTTP 状态语义和不得泄露的错误信息。

不采用直接接收任意业务 DTO 的方案，因为这会使框架依赖宿主领域模型；也不开放任意扩展 `payload`，因为它会绕过属性白名单和敏感数据控制。

## 接入架构

```text
浏览器标准信号
    |
宿主 POST Controller（认证、CSRF/CORS、大小限制、频率限制）
    |
宿主 DTO/适配器（必要时将本系统字段映射为 FrontendSignal）
    |
Schema、对象约束与时钟偏差校验
    |
服务端可信上下文（用户、角色、会话哈希、可信来源 IP、收件时间）
    |
FrontendSignalRecorder.record(...)
    |
SecurityMonitor、规则评估与事件持久化
```

请求中允许的字段、长度、哈希前缀和属性键由 JSON Schema 与 `FrontendSignal` 的构造校验共同限制。`client_event_id`、`route`、`action` 与可选资源标识仅描述客户端观察到的事实，不构成授权或控制断言。

## 请求与字段适配

标准请求必须携带 `client_event_id`、`occurred_at`、`request_id`、`route` 与 `action`。可选设备和资源标识必须是 `sha256:` 前缀的单向摘要；属性只允许 `feature`、`interaction`、`page_type`、`network_type`、`ui_version`、`resource_class` 与 `outcome_hint`。

业务系统的字段名称、枚举和资源结构可以不同，但必须在宿主适配层完成明确的一对一映射。例如订单系统可将 `pageCode` 映射为 `route`、`operationCode` 映射为 `action`、已脱敏订单标识映射为 `resource_id_hash`。未能映射的字段不得放入 `attributes`，除非先扩展发布的 Schema、Java 白名单和相应测试。

`request_id` 必须来源于后端先前签发或当前请求可验证的关联标识；不应仅因浏览器任意提交就将它视为可信。`trace_id` 仅用于关联查询，不能决定事件身份。浏览器不能提交用户 ID、角色、账号类型、会话 ID、源 IP、风险等级、告警 ID、控制动作、授权决定或最终结果。

## 安全校验和响应语义

宿主端点必须先完成认证；使用 Cookie 会话时还必须按宿主规则执行 CSRF 防护，跨域接入必须使用显式 CORS 来源白名单。随后限制 Content-Type 为 JSON、限制请求体大小，并以可信用户和可信 IP 为维度进行频率限制。

端点应先校验 JSON Schema，再构造 `FrontendSignal` 以执行长度、哈希前缀和属性白名单校验；并在调用记录器前使用 `FrontendSignalValidator` 将 `occurred_at` 与服务端收件时间进行偏差校验。用户、角色、会话哈希和来源 IP 必须由已认证的后端请求上下文构造为 `FrontendServerContext`。来源 IP 只能通过已配置的可信代理解析。

组件不规定响应 JSON。宿主应复用自身错误码与错误体，但 HTTP 语义应保持一致：无效或不支持的标准信号为 `400`，未认证为 `401`，无权调用采集端点为 `403`，请求过大为 `413`，频率限制为 `429`，不可恢复的服务端异常为 `5xx`。响应不得回显完整上报内容、会话信息、内部规则、风险分数或控制状态。

`client_event_id` 可作为宿主的幂等键之一。当前组件不提供端到端去重保证；需要至少一次传输重试的宿主应按“可信用户或会话范围 + client_event_id”设定短期去重策略，并保留审计所需的最小结果。

## 代码与文档变更

1. 扩展中文集成指南的前端信号章节，加入协议范围、字段适配表、完整请求示例、宿主 Controller 伪代码、安全检查顺序、状态语义和验收清单。
2. 更新 README 的文档导航，使接入指南可被直接发现；若英文 README 已包含并行文档索引，同步加入对应链接或说明。
3. 如新增统一辅助入口或调整现有接入 API，为所有新增公共类型、构造器和方法补充完整中文 Javadoc，说明输入可信度、调用顺序、异常条件与线程安全约束。示例代码逐行保留必要的中文行内注释，解释安全目的而非复述代码。
4. 不新增 Spring MVC Controller，不规定 URL，不引入 Spring Web 或 Servlet 依赖到 `web-contract`、`core` 或 `spring-support`。HTTP 反序列化、认证和响应仅由宿主实现。

## 测试和验收

保留并扩展 `web-contract` 的 Schema 与对象约束测试，覆盖必填字段、未知属性、非 `sha256:` 标识和超时信号。若修改记录入口，应测试它不会使用浏览器提供的身份、角色、IP、会话或风险结论。

宿主验收至少覆盖：标准信号成功记录；业务 DTO 显式映射为标准信号；未知字段被拒绝；超时信号被拒绝；匿名、CSRF 失败、过大请求和频率超限按宿主模型返回；客户端伪造身份、IP、角色或控制字段不会影响服务端上下文与授权。

## 非目标

本次不提供客户端 SDK、通用 HTTP Controller、统一响应 JSON、自动的跨请求幂等存储或对任意业务字段的动态扩展能力。这些能力由宿主系统按自身网关、认证和 API 规范实现。
