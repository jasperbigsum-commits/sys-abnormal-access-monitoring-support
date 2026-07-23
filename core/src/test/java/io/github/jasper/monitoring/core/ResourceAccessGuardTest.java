package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.core.application.control.ControlHandlerRegistry;
import io.github.jasper.monitoring.core.infrastructure.memory.InMemoryMonitoringRepository;
import io.github.jasper.monitoring.core.domain.rule.DefaultRuleCatalog;
import io.github.jasper.monitoring.core.port.NotificationChannel;
import io.github.jasper.monitoring.core.application.DefaultSecurityMonitor;
import io.github.jasper.monitoring.core.application.authorization.ResourceAccessGuard;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.ResourceScopeAuthorizer;
import io.github.jasper.monitoring.api.ResourceScopeRequest;
import io.github.jasper.monitoring.api.SecurityEventType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ResourceAccessGuardTest {

    @Test
    void preservesDeniedDecisionAndRecordsResourceScopeDeniedEvent() {
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        DefaultSecurityMonitor monitor = new DefaultSecurityMonitor(
            "orders", Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC), repository,
            DefaultRuleCatalog.initialRules(), io.github.jasper.monitoring.api.MonitoringMode.OBSERVE,
            ControlHandlerRegistry.empty(), NotificationChannel.noop());
        ResourceScopeAuthorizer authorizer = new ResourceScopeAuthorizer() {
            @Override
            public AuthorizationDecision authorize(IdentityContext identity, ResourceScopeRequest request) {
                return AuthorizationDecision.denied("RESOURCE_SCOPE_DENIED");
            }
        };
        ResourceAccessGuard guard = new ResourceAccessGuard(authorizer, monitor,
            Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC));
        MonitoringRequestContext request = MonitoringRequestContext.builder()
            .method("GET").path("/orders/o-2").sourceIp("203.0.113.7").requestId("req-2").build();

        AuthorizationDecision decision = guard.authorize(
            new IdentityContext("alice", io.github.jasper.monitoring.api.AccountType.PERSON,
                Collections.singleton("operator"), "session-hash"),
            new ResourceScopeRequest(request, "order", "o-2", "org-b"));

        assertFalse(decision.isAllowed());
        assertEquals(SecurityEventType.RESOURCE_SCOPE_DENIED, repository.getEvents().get(0).getEventType());
        assertEquals("RESOURCE_SCOPE_DENIED", repository.getEvents().get(0).getReasonCode());
    }

    @Test
    void raisesAuthzOneAfterTenResourceScopeDeniedEvents() {
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        DefaultSecurityMonitor monitor = new DefaultSecurityMonitor(
            "orders", Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC), repository,
            DefaultRuleCatalog.initialRules(), io.github.jasper.monitoring.api.MonitoringMode.OBSERVE,
            ControlHandlerRegistry.empty(), NotificationChannel.noop());
        ResourceScopeAuthorizer authorizer = new ResourceScopeAuthorizer() {
            @Override
            public AuthorizationDecision authorize(IdentityContext identity, ResourceScopeRequest request) {
                return AuthorizationDecision.denied("RESOURCE_SCOPE_DENIED");
            }
        };
        ResourceAccessGuard guard = new ResourceAccessGuard(authorizer, monitor,
            Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC));
        IdentityContext identity = new IdentityContext("alice", io.github.jasper.monitoring.api.AccountType.PERSON,
            Collections.singleton("operator"), "session-hash");

        for (int index = 0; index < 10; index++) {
            MonitoringRequestContext request = MonitoringRequestContext.builder()
                .method("GET").path("/orders/o-" + index).sourceIp("203.0.113.7")
                .requestId("req-" + index).build();
            guard.authorize(identity, new ResourceScopeRequest(request, "order", "o-" + index, "org-b"));
        }

        assertEquals(1, repository.getAlerts().size());
        assertEquals("AUTHZ-01", repository.getAlerts().get(0).getRuleId());
    }
}
