package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.ResourceScopeAuthorizer;
import io.github.jasper.monitoring.api.ResourceScopeRequest;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Framework-neutral resource authorization bridge. The host stays authoritative;
 * this class records the resulting decision without ever turning a deny into an allow.
 */
public final class ResourceAccessGuard {
    private final ResourceScopeAuthorizer authorizer;
    private final SecurityMonitor monitor;
    private final Clock clock;

    /**
     * @param authorizer host-owned authorization decision maker; it remains the source of truth
     * @param monitor monitor used only to audit the resulting decision
     * @param clock source of the event timestamp
     */
    public ResourceAccessGuard(ResourceScopeAuthorizer authorizer, SecurityMonitor monitor, Clock clock) {
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.monitor = Objects.requireNonNull(monitor, "monitor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Obtains the host authorization decision and records an allowed or denied access event.
     * A missing or failing authorizer result is converted to a deny; a monitoring failure never changes the decision.
     *
     * @param identity host-resolved identity, or {@code null} to record an anonymous request
     * @param resource requested resource and trusted request metadata
     * @return the host decision, or a fail-closed deny when no usable decision is produced
     */
    public AuthorizationDecision authorize(IdentityContext identity, ResourceScopeRequest resource) {
        AuthorizationDecision decision;
        try {
            decision = authorizer.authorize(identity == null ? IdentityContext.anonymous() : identity, resource);
            if (decision == null) {
                decision = AuthorizationDecision.denied("AUTHORIZATION_NO_DECISION");
            }
        } catch (RuntimeException ignored) {
            decision = AuthorizationDecision.denied("AUTHORIZATION_ERROR");
        }
        recordDecision(identity == null ? IdentityContext.anonymous() : identity, resource, decision);
        return decision;
    }

    private void recordDecision(IdentityContext identity, ResourceScopeRequest resource, AuthorizationDecision decision) {
        try {
            SecurityEventDraft.Builder draft = SecurityEventDraft.builder()
                .eventType(decision.isAllowed() ? SecurityEventType.ACCESS_ALLOWED : SecurityEventType.RESOURCE_SCOPE_DENIED)
                .action(resource.getRequest().getMethod())
                .result(decision.isAllowed() ? SecurityEventResult.SUCCESS : SecurityEventResult.DENIED)
                .sourceIp(resource.getRequest().getSourceIp())
                .requestId(resource.getRequest().getRequestId())
                .traceId(resource.getRequest().getTraceId())
                .userId(identity.getUserId())
                .accountType(identity.getAccountType())
                .roleIds(identity.getRoleIds())
                .sessionIdHash(identity.getSessionIdHash())
                .resourceType(resource.getResourceType())
                .resourceId(resource.getResourceId())
                .orgScope(resource.getOrgScope())
                .reasonCode(decision.getReasonCode())
                .occurredAt(Instant.now(clock));
            monitor.record(draft.build());
        } catch (RuntimeException ignored) {
            // Monitoring failures cannot bypass the host system's established authorization decision.
        }
    }
}
