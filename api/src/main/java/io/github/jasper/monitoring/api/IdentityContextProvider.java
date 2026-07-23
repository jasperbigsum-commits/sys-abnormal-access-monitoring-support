package io.github.jasper.monitoring.api;

/**
 * 连接宿主既有认证子系统的必需适配点。
 *
 * <p>该组件是监测所用主体信息的权威来源。</p>
 */
public interface IdentityContextProvider {
    /**
     * 使用可信的服务端认证状态解析请求身份。
     *
     * @param request 框架适配器提供且已清洗的请求事实
     * @return 解析后的身份；无身份时返回 {@link IdentityContext#anonymous()}
     */
    IdentityContext resolve(MonitoringRequestContext request);
}
