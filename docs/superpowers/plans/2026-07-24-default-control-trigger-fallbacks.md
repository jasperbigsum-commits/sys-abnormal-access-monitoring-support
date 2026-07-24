# 默认控制触发器与事件属性补充 Implementation Plan

**Goal:** 为每个非 `RECORD` 的 `ControlActionType` 提供安全的框架回退触发器，同时允许宿主 `ControlHandler` 或 `@ControlTrigger` 以同动作覆盖；完善接入指南中的触发分类、默认实现、参数和安全事件属性补充说明。

**Safety boundary:** 回退触发器只能返回已审计的 `SKIPPED`，不能实施通用拒绝、限流、锁定或审批。它不计入宿主可执行控制能力，因此单独存在时仍不能启动 `ENFORCE`。

## Steps

1. 为 `ControlHandlerRegistry` 增加宿主/默认两层解析，保留单列表构造以兼容既有调用；添加由 `ControlActionType` 驱动的默认回退处理器。
2. 在 Boot 2 与 Boot 3 自动配置中将宿主接口实现和注解绑定放入宿主层，将默认回退处理器放入默认层，确保宿主同动作优先。
3. 先补充注册表与两个 Starter 的回归测试：宿主覆盖、回退跳过、默认不满足 `ENFORCE`、完整注册表覆盖自动配置。
4. 更新 `EventEnricher` 契约和集成指南：列出可补充的资源、范围、计数、时延、原因码和非敏感属性，明确不可覆盖的可信主字段与敏感字段禁令。
5. 运行 API/Core/两个 Starter 的聚焦测试，随后执行完整 Maven reactor 验证并检查文档差异。
