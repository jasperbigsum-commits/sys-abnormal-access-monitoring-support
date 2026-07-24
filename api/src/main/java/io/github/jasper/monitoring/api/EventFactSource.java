package io.github.jasper.monitoring.api;

/**
 * 可写入监测输入质量诊断的受控事实来源。
 */
public enum EventFactSource {
    /** 由动作注解或注册表固定声明。 */
    STATIC_DECLARATION,
    /** 由已验证的请求上下文提供。 */
    TRUSTED_REQUEST,
    /** 由宿主已认证身份提供。 */
    TRUSTED_IDENTITY,
    /** 由已声明的方法参数路径提取。 */
    METHOD_PARAMETER,
    /** 由服务端执行过程或返回结果计算。 */
    SERVER_COMPUTED,
    /** 由宿主授权结论提供。 */
    AUTHORIZATION,
    /** 由受控事件补充器提供。 */
    EVENT_ENRICHER
}
