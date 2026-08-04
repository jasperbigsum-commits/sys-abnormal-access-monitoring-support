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
import io.github.jasper.monitoring.core.domain.WhitelistEntry;
import io.github.jasper.monitoring.core.port.WhitelistRepository;
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
    void activeExplicitlyScopedPassCanOverrideAHostDenial() {
        WhitelistRepository passes = new WhitelistRepository() {
            @Override public boolean isActive(String systemId, String ruleId, String subject, Instant at) {
                return "orders".equals(systemId) && "AUTHZ-01".equals(ruleId)
                    && "user:user-1".equals(subject);
            }
            @Override public void add(WhitelistEntry entry) { }
        };
        ResourceAccessGuard guard = guard(
            (identity, request) -> AuthorizationDecision.denied(
                io.github.jasper.monitoring.api.code.BuiltInReasonCodes.Authorization.RESOURCE_SCOPE_DENIED),
            new RecordingEvents(), passes);
        ResourceScopeRequest resource = new ResourceScopeRequest(resource("report-1").getRequest(),
            "REPORT", "report-1", "org-1",
            "AUTHZ-01", "user:user-1");

        assertTrue(guard.authorize(identity(), resource).isAllowed());
    }

    @Test
    void passCannotOverrideAnAuthorizerFailure() {
        WhitelistRepository passes = new WhitelistRepository() {
            @Override public boolean isActive(String systemId, String ruleId, String subject, Instant at) {
                return true;
            }
            @Override public void add(WhitelistEntry entry) { }
        };
        ResourceAccessGuard guard = guard((identity, request) -> {
            throw new IllegalStateException("authorization unavailable");
        }, new RecordingEvents(), passes);
        ResourceScopeRequest resource = new ResourceScopeRequest(resource("report-1").getRequest(),
            "REPORT", "report-1", "org-1", "AUTHZ-01", "user:user-1");

        assertFalse(guard.authorize(identity(), resource).isAllowed());
    }

    @Test
    void preservesDeniedDecisionAndRecordsTypedAccessDeniedAction() {
        RecordingEvents events = new RecordingEvents();
        ResourceAccessGuard guard = guard(
            (identity, request) -> AuthorizationDecision.denied(
                io.github.jasper.monitoring.api.code.BuiltInReasonCodes.Authorization.RESOURCE_SCOPE_DENIED), events);

        AuthorizationDecision decision = guard.authorize(identity(), resource("o-2"));

        assertFalse(decision.isAllowed());
        assertEquals(SecurityEventType.ACCESS_DENIED, events.single().getEventType());
        assertEquals("authz:access-denied", events.single().getAction());
        assertEquals("o-2", events.single().getResourceId());
        assertEquals("MON.AUTHZ.RESOURCE_SCOPE_DENIED", events.single().getReasonCode());
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
        return guard(authorizer, events, new WhitelistRepository() {
            @Override public boolean isActive(String systemId, String ruleId, String subject, Instant at) {
                return false;
            }
            @Override public void add(WhitelistEntry entry) { }
        });
    }

    private static ResourceAccessGuard guard(ResourceScopeAuthorizer authorizer, EventRepository events,
            WhitelistRepository passes) {
        ActionCatalog catalog = new ActionCatalog();
        BuiltInActions.registerInto(catalog);
        catalog.freeze();
        Clock clock = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC);
        MonitoringService monitoring = new MonitoringService(events,
            new SecurityEventAssembler("orders", clock),
            new DefaultMonitoringRuntime(catalog, builtInFacts(), Collections.emptyList()),
            (type, action, event, facts, sources, ineligible, issues) -> { }, stableCodes());
        return new ResourceAccessGuard("orders", authorizer, passes, monitoring, clock);
    }

    private static io.github.jasper.monitoring.api.fact.FactCatalog builtInFacts() {
        io.github.jasper.monitoring.api.fact.FactCatalog catalog =
            new io.github.jasper.monitoring.api.fact.FactCatalog();
        io.github.jasper.monitoring.api.fact.BuiltInFacts.registerInto(catalog);
        catalog.freeze();
        return catalog;
    }

    private static io.github.jasper.monitoring.api.code.StableCodeCatalog stableCodes() {
        io.github.jasper.monitoring.api.code.StableCodeCatalog catalog =
            new io.github.jasper.monitoring.api.code.StableCodeCatalog("");
        io.github.jasper.monitoring.api.code.BuiltInReasonCodes.registerInto(catalog);
        catalog.freeze();
        return catalog;
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
