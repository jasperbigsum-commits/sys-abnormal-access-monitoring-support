package io.github.jasper.monitoring.api;

/**
 * 标识监测请求关联的主体类型。
 *
 * <p>该值必须由宿主系统的认证模块生成，绝不能采信浏览器上报的主体类型。</p>
 */
public enum AccountType {
    /** 已认证的自然人用户。 */
    PERSON,
    /** 服务、任务负载或机器身份。 */
    SERVICE,
    /** 无法获得已认证主体的请求。 */
    ANONYMOUS
}
