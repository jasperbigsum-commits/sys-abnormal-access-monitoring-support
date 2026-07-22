package io.github.jasper.monitoring.api;

/**
 * Required host-side bridge to its established authentication subsystem.
 *
 * <p>This component is the authoritative source of the principal used for monitoring.</p>
 */
public interface IdentityContextProvider {
    /**
     * Resolves the identity for a request using trusted server-side authentication state.
     *
     * @param request sanitized request facts supplied by the framework adapter
     * @return the resolved identity, or {@link IdentityContext#anonymous()} when no identity exists
     */
    IdentityContext resolve(MonitoringRequestContext request);
}
