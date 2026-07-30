# 全局 Starter 配置开关设计

## 背景与目标

当前三个 Spring Boot Starter 会在依赖进入应用后默认装配监控运行时。项目已经为前端信号、自动埋点、MDC、通知重试和 IP 控制提供局部开关，但缺少一个能够完整关闭集成的总开关。

新增 `abnormal.access.monitor.enabled`。该属性默认值为 `true`，保持现有应用行为；显式配置为 `false` 时，Starter 不应注册任何监控组件。Spring Boot 2.1、2.7 和 3 的行为必须一致。

## 配置契约

```yaml
abnormal:
  access:
    monitor:
      enabled: false
```

- 属性名称：`abnormal.access.monitor.enabled`
- 类型：布尔值
- 默认值：`true`
- 缺少属性时：视为启用，以保证向后兼容
- 显式设为 `false` 时：整个 Starter 自动配置不生效

局部开关仅在全局开关启用时有意义。全局开关关闭后，不再分别解释或执行 `frontend.enabled`、`instrumentation.enabled`、`mdc.enabled`、`notification.retry-enabled` 和 `ip-control.enabled`。

## 装配边界

在以下三套 `AbnormalAccessMonitorAutoConfiguration` 类上增加类级 `@ConditionalOnProperty`：

- `spring2-legacy-starter`，Spring Boot 2.1 / Java 8
- `spring2-starter`，Spring Boot 2.7 / Java 8
- `spring3-starter`，Spring Boot 3 / Java 17

条件使用前缀 `abnormal.access.monitor`、名称 `enabled`、期望值 `true`，并设置 `matchIfMissing = true`。

类级条件关闭整套自动配置，包括监控运行时、持久化适配、Servlet 请求上下文、拦截器、注解切面、调度任务、管理服务、通知和 IP 控制。关闭状态不注册空实现或降级 Bean，也不要求 `SqlSessionFactory`、宿主 SPI 或控制处理器存在。

三个 `AbnormalAccessMonitorProperties` 类增加默认值为 `true` 的 `enabled` 字段及标准访问器，使属性模型、IDE 元数据与运行条件表达同一契约。三套 `spring-configuration-metadata.json` 同步声明该属性、默认值和关闭语义。

## 兼容性与安全

缺少新属性时保持当前默认装配行为，因此现有集成无需修改配置。关闭开关只移除本组件提供的监测、告警和控制能力，不得影响宿主原有身份认证、授权、Servlet 过滤链或业务 Bean。

该开关适合未完成集成、故障隔离和按环境逐步启用。生产环境关闭后将不再产生本组件的安全事件或执行本组件控制，因此文档必须明确其可观测性影响。

## 测试设计

三套 Starter 使用各自现有的应用上下文测试，覆盖以下场景：

1. 未配置 `abnormal.access.monitor.enabled` 时，代表性核心 Bean 仍然存在。
2. 配置 `abnormal.access.monitor.enabled=true` 时，行为与默认状态一致。
3. 配置 `abnormal.access.monitor.enabled=false` 时，上下文可在缺少监控集成依赖的情况下启动，且不存在配置属性 Bean、监控服务、Servlet/AOP 集成、调度和可选控制 Bean。
4. 配置全局关闭但局部开关为开启时，全局关闭优先，不产生局部功能 Bean。

实现完成后运行三套 Starter 的聚焦自动配置测试，再运行 Maven reactor 全量验证。

## 文档范围

中文和英文集成指南增加总开关说明及 YAML 示例。配置元数据是完整属性定义的权威来源，指南只解释启用默认值、关闭范围和安全影响。

## 非目标

- 不增加运行期动态切换；该属性只在 Spring 应用上下文启动时判定。
- 不改变现有局部开关的默认值或语义。
- 不引入无操作监控实现，也不在关闭时保留仅供探测的 Starter Bean。
- 不改变 `OBSERVE` 与 `ENFORCE` 模式定义；模式只在全局启用时生效。
