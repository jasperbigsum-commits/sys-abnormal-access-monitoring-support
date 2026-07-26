package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.ResourceScopeAuthorizer;
import io.github.jasper.monitoring.api.ResourceScopeRequest;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.action.ActionCatalog;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.core.application.DefaultMonitoringRuntime;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import io.github.jasper.monitoring.core.application.authorization.ResourceAccessGuard;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.port.EventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceAccessGuardTest {

    @Test
    void preservesDeniedDecisionAndRecordsTypedAccessDeniedAction() {
        RecordingEvents events = new RecordingEvents();
        ResourceAccessGuard guard = guard(
            (identity, request) -> AuthorizationDecision.denied("RESOURCE_SCOPE_DENIED"), events);

        AuthorizationDecision decision = guard.authorize(identity(), resource("o-2"));

        assertFalse(decision.isAllowed());
        assertEquals(SecurityEventType.ACCESS_DENIED, events.single().getEventType());
        assertEquals("authz:access-denied", events.single().getAction());
        assertEquals("o-2", events.single().getResourceId());
        assertEquals("RESOURCE_SCOPE_DENIED", events.single().getReasonCode());
    }

    @Test
    void recordsAllowedDecisionAsASeparateTypedAction() {
        RecordingEvents events = new RecordingEvents();
        ResourceAccessGuard guard = guard((identity, request) -> AuthorizationDecision.allowed(), events);

        AuthorizationDecision decision = guard.authorize(identity(), resource("o-1"));

        assertTrue(decision.isAllowed());
        assertEquals(SecurityEventType.ACCESS_ALLOWED, events.single().getEventType());
        assertEquals("authz:access-allowed", events.single().getAction());
    }

    @Test
    void monitoringFailureCannotChangeTheHostAuthorizationDecision() {
        EventRepository failingEvents = new RecordingEvents() {
            @Override
            public void save(SecurityEvent event) {
                throw new IllegalStateException("storage unavailable");
            }
        };
        ResourceAccessGuard guard = guard((identity, request) -> AuthorizationDecision.allowed(), failingEvents);

        assertTrue(guard.authorize(identity(), resource("o-1")).isAllowed());
    }

    private static ResourceAccessGuard guard(ResourceScopeAuthorizer authorizer, EventRepository events) {
        ActionCatalog catalog = new ActionCatalog();
        BuiltInActions.registerInto(catalog);
        catalog.freeze();
        Clock clock = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC);
        MonitoringService monitoring = new MonitoringService(events,
            new SecurityEventAssembler("orders", clock),
            new DefaultMonitoringRuntime(catalog, Collections.emptyList()),
            (type, action, event, facts, ineligible, issues) -> { });
        return new ResourceAccessGuard(authorizer, monitoring);
    }

    private static IdentityContext identity() {
        return new IdentityContext("alice", io.github.jasper.monitoring.api.AccountType.PERSON,
            Collections.singleton("operator"), "session-hash");
    }

    private static ResourceScopeRequest resource(String id) {
        MonitoringRequestContext request = MonitoringRequestContext.builder()
            .method("GET").path("/orders/" + id).sourceIp("203.0.113.7").requestId("req-" + id).build();
        return new ResourceScopeRequest(request, "order", id, "org-b");
    }

    static class RecordingEvents implements EventRepository {
        private final List<SecurityEvent> events = new ArrayList<SecurityEvent>();

        @Override
        public void save(SecurityEvent event) {
            events.add(event);
        }

        @Override
        public Optional<SecurityEvent> findEvent(String eventId) {
            return events.stream().filter(event -> event.getEventId().equals(eventId)).findFirst();
        }

        @Override
        public List<SecurityEvent> findSince(String systemId, Instant since) {
            return new ArrayList<SecurityEvent>(events);
        }

        SecurityEvent single() {
            assertEquals(1, events.size());
            return events.get(0);
        }
    }
}
