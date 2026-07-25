package io.github.jasper.monitoring.api;

/**
 * 访问当前请求可信监测上下文的统一入口。
 *
 * <p>具体 Web 框架负责实现该接口；业务代码不需要依赖 Servlet 请求属性或属性名称。</p>
 */
public interface MonitoringContextAccessor {
    /**
     * @return 当前请求的可信请求上下文
     * @throws IllegalStateException 当前不在已建立监测上下文的请求中
     */
    MonitoringRequestContext requestContext();

    /**
     * @return 当前请求的服务端身份上下文
     * @throws IllegalStateException 当前不在已建立监测上下文的请求中
     */
    IdentityContext identityContext();
}
