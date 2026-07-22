package io.github.jasper.monitoring.api;

/**
 * Authoritative resource-level authorization decision supplied by the host system.
 *
 * <p>The monitoring component observes this decision but never expands access beyond it.</p>
 */
public interface ResourceScopeAuthorizer {
    /**
     * Decides whether an identity may access a requested resource scope.
     *
     * @param identity trusted identity resolved by the host backend
     * @param request request and resource facts to authorize
     * @return explicit allow or deny decision; implementations should fail closed on uncertainty
     */
    AuthorizationDecision authorize(IdentityContext identity, ResourceScopeRequest request);
}
