package io.github.jasper.monitoring.api;

/**
 * 从宿主系统认可的代理链解析客户端网络地址（IP）。
 *
 * <p>当直连对端不可信时，实现必须忽略其传入的转发地址请求头。</p>
 */
public interface TrustedProxyResolver {
    /**
     * 解析一次请求的可信客户端地址。
     *
     * @param directRemoteAddress 传输连接报告的直连对端地址
     * @param forwardedForHeader 应用收到的可选转发地址请求头
     * @return 应写入安全事件的可信客户端地址
     */
    String resolveClientIp(String directRemoteAddress, String forwardedForHeader);
}
