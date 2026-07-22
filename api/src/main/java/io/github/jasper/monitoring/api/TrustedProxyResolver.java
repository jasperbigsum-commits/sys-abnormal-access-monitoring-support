package io.github.jasper.monitoring.api;

/**
 * Resolves a client IP from a host-approved proxy chain.
 *
 * <p>Implementations must ignore forwarded-address headers from untrusted direct peers.</p>
 */
public interface TrustedProxyResolver {
    /**
     * Resolves the client address for a request.
     *
     * @param directRemoteAddress address reported by the transport connection
     * @param forwardedForHeader optional forwarded-address header supplied to the application
     * @return the trusted client address to place on the event
     */
    String resolveClientIp(String directRemoteAddress, String forwardedForHeader);
}
