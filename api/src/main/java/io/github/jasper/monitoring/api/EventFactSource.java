package io.github.jasper.monitoring.api;

/**
 * 输入质量诊断使用的事实来源分类。
 * <p>
 * 与 {@link io.github.jasper.monitoring.api.fact.FactSource} 的区别：
 * 这里面向“诊断记录”语义（问题从哪里产生），用于追踪事实缺失/冲突的责任路径。
 */
public enum EventFactSource {
    /** 静态声明来源：动作定义、注解、目录注册时就能确定的元信息。 */
    STATIC_DECLARATION,
    /** 可信请求来源：来自已校验请求上下文（例如网关透传且已验签字段）。 */
    TRUSTED_REQUEST,
    /** 可信身份来源：来自已认证主体（例如用户ID、账号类型、角色集合）。 */
    TRUSTED_IDENTITY,
    /** 方法参数来源：由 @ActionFact 参数或 path 路径解析得到。 */
    METHOD_PARAMETER,
    /** 服务端计算来源：执行链路中计算或归一化得到（如耗时分段、统计值）。 */
    SERVER_COMPUTED,
    /** 授权结论来源：由宿主授权器给出的允许/拒绝及其范围判断。 */
    AUTHORIZATION,
    /** 事件补充器来源：由受控 enricher 在事件落库前补充的额外字段。 */
    EVENT_ENRICHER
}
