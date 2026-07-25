package io.github.jasper.monitoring.spring.support.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.ControlStatus;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class GenericIpControlHandlerTest {
    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration MAX_TTL = Duration.ofMinutes(30);

    @Test
    void activatesAnAllowedRateLimitForACanonicalIpv4Subject() {
        RecordingState state = new RecordingState(IpControlState.ActivationResult.ACTIVATED);
        GenericIpControlHandler handler = handler(state);

        ControlExecution execution = handler.execute(command("AUTH-02", "ip:203.0.113.10",
            ControlActionType.RATE_LIMIT, NOW.plusSeconds(60)));

        assertEquals(ControlStatus.SUCCEEDED, execution.getStatus());
        assertEquals("203.0.113.10", state.canonicalIp);
        assertEquals(ControlActionType.RATE_LIMIT, state.action);
        assertEquals(NOW.plusSeconds(60), state.expiresAt);
        assertEquals(NOW, state.now);
    }

    @Test
    void canonicalizesIpv6WithoutResolvingHostNames() {
        RecordingState state = new RecordingState(IpControlState.ActivationResult.ACTIVATED);

        ControlExecution execution = handler(state).execute(command("AUTH-02", "ip:2001:0DB8:0:0:0:0:0:1",
            ControlActionType.DENY, NOW.plusSeconds(60)));

        assertEquals(ControlStatus.SUCCEEDED, execution.getStatus());
        assertEquals("2001:db8::1", state.canonicalIp);

        handler(state).execute(command("AUTH-02", "ip:::ffff:192.0.2.1",
            ControlActionType.DENY, NOW.plusSeconds(60)));
        assertEquals("::ffff:c000:201", state.canonicalIp);
    }

    @Test
    void supportsOnlyRateLimitAndDeny() {
        GenericIpControlHandler handler = handler(new RecordingState(IpControlState.ActivationResult.ACTIVATED));

        assertTrue(handler.supports(ControlActionType.RATE_LIMIT));
        assertTrue(handler.supports(ControlActionType.DENY));
        assertFalse(handler.supports(ControlActionType.LOCK));
        assertEquals("GENERIC_IP_ACTION_UNSUPPORTED",
            handler.execute(command("AUTH-02", "ip:203.0.113.10", ControlActionType.LOCK,
                NOW.plusSeconds(60))).getFailureReason());
    }

    @Test
    void skipsMissingOrUnallowlistedRulesWithoutLeakingRuleIds() {
        GenericIpControlHandler handler = handler(new RecordingState(IpControlState.ActivationResult.ACTIVATED));

        ControlExecution missing = handler.execute(command(null, "ip:203.0.113.10",
            ControlActionType.DENY, NOW.plusSeconds(60)));
        ControlExecution unallowlisted = handler.execute(command("SECRET-RULE", "ip:203.0.113.10",
            ControlActionType.DENY, NOW.plusSeconds(60)));

        assertSkipped(missing, "GENERIC_IP_RULE_NOT_ALLOWED");
        assertSkipped(unallowlisted, "GENERIC_IP_RULE_NOT_ALLOWED");
    }

    @Test
    void skipsNonIpAndNonCanonicalIpSubjectsWithStableReasons() {
        GenericIpControlHandler handler = handler(new RecordingState(IpControlState.ActivationResult.ACTIVATED));

        assertSkipped(handler.execute(command("AUTH-02", "user:alice", ControlActionType.DENY,
            NOW.plusSeconds(60))), "GENERIC_IP_SUBJECT_REQUIRED");
        assertSkipped(handler.execute(command("AUTH-02", "ip:example.com", ControlActionType.DENY,
            NOW.plusSeconds(60))), "GENERIC_IP_LITERAL_REQUIRED");
        assertSkipped(handler.execute(command("AUTH-02", "ip:203.0.113.010", ControlActionType.DENY,
            NOW.plusSeconds(60))), "GENERIC_IP_LITERAL_REQUIRED");
        assertSkipped(handler.execute(command("AUTH-02", "ip:4294967297.0.0.1", ControlActionType.DENY,
            NOW.plusSeconds(60))), "GENERIC_IP_LITERAL_REQUIRED");
        assertSkipped(handler.execute(command("AUTH-02",
            "ip:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            ControlActionType.DENY, NOW.plusSeconds(60))), "GENERIC_IP_LITERAL_REQUIRED");
    }

    @Test
    void skipsExpiredAndOverLimitTtlWithoutCallingState() {
        RecordingState state = new RecordingState(IpControlState.ActivationResult.ACTIVATED);
        GenericIpControlHandler handler = handler(state);

        assertSkipped(handler.execute(command("AUTH-02", "ip:203.0.113.10", ControlActionType.DENY, NOW)),
            "GENERIC_IP_CONTROL_EXPIRED");
        assertSkipped(handler.execute(command("AUTH-02", "ip:203.0.113.10", ControlActionType.DENY,
            NOW.plus(MAX_TTL).plusSeconds(1))), "GENERIC_IP_CONTROL_TTL_EXCEEDED");
        assertEquals(0, state.calls);
    }

    @Test
    void mapsCapacityAndStateFailuresToStableFailedResults() {
        GenericIpControlHandler capacityHandler = handler(
            new RecordingState(IpControlState.ActivationResult.CAPACITY_REJECTED));
        RecordingState throwingState = new RecordingState(IpControlState.ActivationResult.ACTIVATED);
        throwingState.failure = new IllegalStateException("sensitive internal detail");

        ControlExecution capacity = capacityHandler.execute(command("AUTH-02", "ip:203.0.113.10",
            ControlActionType.DENY, NOW.plusSeconds(60)));
        ControlExecution failed = handler(throwingState).execute(command("AUTH-02", "ip:203.0.113.10",
            ControlActionType.DENY, NOW.plusSeconds(60)));

        assertFailed(capacity, "GENERIC_IP_CONTROL_CAPACITY_REJECTED");
        assertFailed(failed, "GENERIC_IP_CONTROL_STATE_FAILED");
    }

    @Test
    void mapsIdempotentActivationToSucceededReplay() {
        GenericIpControlHandler handler = handler(
            new RecordingState(IpControlState.ActivationResult.IDEMPOTENT_REPLAY));

        ControlExecution execution = handler.execute(command("AUTH-02", "ip:203.0.113.10",
            ControlActionType.DENY, NOW.plusSeconds(60)));

        assertEquals(ControlStatus.SUCCEEDED, execution.getStatus());
        assertTrue(execution.isIdempotentReplay());
        assertNull(execution.getFailureReason());
    }

    @Test
    void validatesConstructorConfiguration() {
        boolean rejectedEmptyAllowlist = false;
        boolean rejectedNonPositiveTtl = false;
        try {
            new GenericIpControlHandler(new RecordingState(IpControlState.ActivationResult.ACTIVATED),
                Collections.<String>emptySet(), MAX_TTL, CLOCK);
        } catch (IllegalArgumentException expected) {
            rejectedEmptyAllowlist = true;
        }
        try {
            new GenericIpControlHandler(new RecordingState(IpControlState.ActivationResult.ACTIVATED),
                Collections.singleton("AUTH-02"), Duration.ZERO, CLOCK);
        } catch (IllegalArgumentException expected) {
            rejectedNonPositiveTtl = true;
        }

        assertTrue(rejectedEmptyAllowlist);
        assertTrue(rejectedNonPositiveTtl);
    }

    private static GenericIpControlHandler handler(IpControlState state) {
        return new GenericIpControlHandler(state, Collections.singleton("AUTH-02"), MAX_TTL, CLOCK);
    }

    private static ControlCommand command(String ruleId, String subject, ControlActionType action,
                                          Instant expiresAt) {
        return new ControlCommand("control-key", "alert-id", subject, action, expiresAt, ruleId);
    }

    private static void assertSkipped(ControlExecution execution, String reason) {
        assertEquals(ControlStatus.SKIPPED, execution.getStatus());
        assertEquals(reason, execution.getFailureReason());
    }

    private static void assertFailed(ControlExecution execution, String reason) {
        assertEquals(ControlStatus.FAILED, execution.getStatus());
        assertEquals(reason, execution.getFailureReason());
    }

    private static final class RecordingState implements IpControlState {
        private final ActivationResult result;
        private RuntimeException failure;
        private int calls;
        private String canonicalIp;
        private ControlActionType action;
        private Instant expiresAt;
        private Instant now;

        private RecordingState(ActivationResult result) {
            this.result = result;
        }

        @Override
        public ActivationResult activate(String idempotencyKey, String canonicalIp,
                                         ControlActionType action, Instant expiresAt, Instant now) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            this.canonicalIp = canonicalIp;
            this.action = action;
            this.expiresAt = expiresAt;
            this.now = now;
            return result;
        }

        @Override
        public IpControlDecision check(String canonicalIp, Instant now) {
            return IpControlDecision.allowed();
        }
    }
}
