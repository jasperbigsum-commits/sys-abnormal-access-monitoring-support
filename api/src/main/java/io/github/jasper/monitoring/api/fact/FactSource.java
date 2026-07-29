package io.github.jasper.monitoring.api.fact;

/**
 * 监测 Fact 的来源分类（同时表达信任边界）。
 * <p>
 * 目的：
 * 同一个 Fact（例如 resource_id）可能来自不同采集路径。来源决定该值能否用于规则判断、
 * 是否可作为强约束依据，以及在输入质量审计中如何解释“证据强弱”。
 */
public enum FactSource {
    /**
     * 服务端可信请求上下文。
     * 场景：网关/服务端已校验并归一化后的请求信息（如租户、资源路由、网关透传字段）。
     */
    TRUSTED_REQUEST,
    /**
     * 服务端可信身份上下文。
     * 场景：从已认证会话、SSO 或安全框架中得到的 userId/subject/roles。
     */
    TRUSTED_IDENTITY,
    /**
     * 方法参数绑定提取值。
     * 场景：通过 @ActionFact 从 Controller/Service 参数中提取 dataCount、targetUserId。
     */
    METHOD_PARAMETER,
    /**
     * 宿主显式注册的 Provider 产出值。
     * 场景：ActionFactProvider 基于业务上下文计算 baselineRatio、sensitivity。
     */
    HOST_PROVIDER,
    /**
     * 客户端补充遥测（非权威证据）。
     * 场景：前端页面路由、交互轨迹；可辅助分析，不可替代身份/授权结论。
     */
    CLIENT_SUPPLEMENTAL,
    /**
     * 框架在结果处理阶段确定的值。
     * 场景：拦截器/AOP 在执行后写入 outcome、异常类型、耗时分档等派生事实。
     */
    FRAMEWORK_OUTCOME
}
