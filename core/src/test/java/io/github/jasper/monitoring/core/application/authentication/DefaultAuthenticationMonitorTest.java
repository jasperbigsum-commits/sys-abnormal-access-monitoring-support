package io.github.jasper.monitoring.core.application.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.action.ActionCatalog;
import io.github.jasper.monitoring.api.action.ActionDisposition;
import io.github.jasper.monitoring.api.action.ActionFailurePolicy;
import io.github.jasper.monitoring.api.action.ActionRequirement;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.authentication.AuthenticationStage;
import io.github.jasper.monitoring.api.authentication.LoginSubjectInput;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.api.code.StableCodeCatalog;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactCatalog;
import io.github.jasper.monitoring.core.application.DefaultMonitoringRuntime;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.port.AuthenticationControlRepository;
import io.github.jasper.monitoring.core.port.EventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultAuthenticationMonitorTest {
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    @Test void mergesAccountAndIpControlsWithBlockingPrecedence() {
        Fixture fixture = fixture((system, subject, at) -> subject.startsWith("ip:")
            ? Collections.singletonList(command(ControlActionType.RATE_LIMIT, "AUTH-02"))
            : Collections.singletonList(command(ControlActionType.REQUIRE_CAPTCHA, "AUTH-01")),
            ActionFailurePolicy.OBSERVE_ONLY);

        io.github.jasper.monitoring.api.action.ActionDecision decision = fixture.monitor.preCheck(input());

        assertEquals(ActionDisposition.BLOCK, decision.getDisposition());
        assertTrue(decision.getRequirements().contains(ActionRequirement.CAPTCHA));
        assertTrue(decision.getMatchedRuleIds().contains("AUTH-01"));
        assertTrue(decision.getMatchedRuleIds().contains("AUTH-02"));
    }

    @Test void recordsDeniedWithAnonymousActorAndTypedFacts() {
        Fixture fixture = fixture((system, subject, at) -> Collections.emptyList(),
            ActionFailurePolicy.OBSERVE_ONLY);

        fixture.monitor.recordDenied(input(), AuthenticationStage.CAPTCHA,
            BuiltInReasonCodes.Authentication.CAPTCHA_INVALID);

        SecurityEvent event = fixture.events.events.get(0);
        assertEquals(SecurityEventResult.DENIED, event.getResult());
        assertNull(event.getUserId());
        assertEquals(AccountType.ANONYMOUS, event.getAccountType());
        assertEquals(AuthenticationStage.CAPTCHA, event.getFact(BuiltInFacts.AUTHENTICATION_STAGE).get());
        assertTrue(event.getFact(BuiltInFacts.LOGIN_SUBJECT_KEY).get().startsWith("v1:"));
    }

    @Test void successRequiresAuthenticatedIdentityAndEmitsSuccessEvent() {
        Fixture fixture = fixture((system, subject, at) -> Collections.emptyList(),
            ActionFailurePolicy.OBSERVE_ONLY);

        assertThrows(IllegalArgumentException.class,
            () -> fixture.monitor.recordSuccess(input(), IdentityContext.anonymous()));
        fixture.monitor.recordSuccess(input(), new IdentityContext("user-1", AccountType.PERSON,
            Collections.<String>emptySet(), null));

        assertEquals(io.github.jasper.monitoring.api.SecurityEventType.LOGIN_SUCCESS,
            fixture.events.events.get(0).getEventType());
        assertEquals("user-1", fixture.events.events.get(0).getUserId());
    }

    @Test void controlFailureUsesConfiguredPolicy() {
        AuthenticationControlRepository broken = (system, subject, at) -> { throw new IllegalStateException("down"); };

        assertTrue(fixture(broken, ActionFailurePolicy.OBSERVE_ONLY).monitor.preCheck(input()).isAllowed());
        assertEquals(ActionDisposition.BLOCK,
            fixture(broken, ActionFailurePolicy.FAIL_CLOSED).monitor.preCheck(input()).getDisposition());
    }

    private static Fixture fixture(AuthenticationControlRepository controls, ActionFailurePolicy policy) {
        RecordingEvents events = new RecordingEvents();
        ActionCatalog actions = new ActionCatalog();
        BuiltInActions.registerInto(actions);
        actions.freeze();
        FactCatalog facts = new FactCatalog();
        BuiltInFacts.registerInto(facts);
        facts.freeze();
        StableCodeCatalog codes = new StableCodeCatalog("");
        BuiltInReasonCodes.registerInto(codes);
        codes.freeze();
        MonitoringService service = new MonitoringService(events,
            new SecurityEventAssembler("test-system", Clock.fixed(NOW, ZoneOffset.UTC)),
            new DefaultMonitoringRuntime(actions, facts, Collections.emptyList()),
            (type, definition, event, actionFacts, sources, ineligible, issues) -> { }, codes);
        MonitoringContextAccessor context = new MonitoringContextAccessor() {
            @Override public MonitoringRequestContext requestContext() { return request(); }
            @Override public IdentityContext identityContext() { return IdentityContext.anonymous(); }
        };
        LoginSubjectKeyFactory keys = new LoginSubjectKeyFactory(
            "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8),
            subject -> subject.getLoginUser().trim().toLowerCase(java.util.Locale.ROOT));
        return new Fixture(new DefaultAuthenticationMonitor("test-system", keys, controls, service,
            context, Clock.fixed(NOW, ZoneOffset.UTC), policy), events);
    }

    private static LoginSubjectInput input() { return new LoginSubjectInput("Alice", "primary"); }
    private static MonitoringRequestContext request() {
        return MonitoringRequestContext.builder().method("POST").path("/login").sourceIp("203.0.113.8")
            .requestId("req-1").traceId("trace-1").build();
    }
    private static ControlCommand command(ControlActionType action, String ruleId) {
        return new ControlCommand("test-system", "key-" + action, "alert", "subject", action,
            NOW.plusSeconds(60), ruleId);
    }

    private static final class Fixture {
        private final DefaultAuthenticationMonitor monitor;
        private final RecordingEvents events;
        private Fixture(DefaultAuthenticationMonitor monitor, RecordingEvents events) {
            this.monitor = monitor;
            this.events = events;
        }
    }
    private static final class RecordingEvents implements EventRepository {
        private final List<SecurityEvent> events = new ArrayList<SecurityEvent>();
        @Override public void save(SecurityEvent event) { events.add(event); }
        @Override public Optional<SecurityEvent> findEvent(String id) { return Optional.empty(); }
        @Override public List<SecurityEvent> findSince(String system, Instant since) {
            return new ArrayList<SecurityEvent>(events);
        }
    }
}
