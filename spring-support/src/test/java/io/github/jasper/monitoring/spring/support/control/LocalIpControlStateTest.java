package io.github.jasper.monitoring.spring.support.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.ControlActionType;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LocalIpControlStateTest {
    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");
    private static final String IP = "203.0.113.10";

    @Test
    void denyWinsOverRateLimitAndExpiresAtTheConfiguredTime() {
        LocalIpControlState state = state(4, 2, Duration.ofSeconds(10));

        assertEquals(IpControlState.ActivationResult.ACTIVATED,
            state.activate("deny-1", IP, ControlActionType.DENY, NOW.plusSeconds(60), NOW));
        assertEquals(IpControlState.ActivationResult.ACTIVATED,
            state.activate("rate-1", IP, ControlActionType.RATE_LIMIT, NOW.plusSeconds(60), NOW));

        assertEquals(IpControlDecision.denied(), state.check(IP, NOW));
        assertEquals(IpControlDecision.allowed(), state.check(IP, NOW.plusSeconds(61)));
    }

    @Test
    void idempotentReplayKeepsTheFirstActionAndExpiry() {
        LocalIpControlState state = state(4, 2, Duration.ofSeconds(10));

        assertEquals(IpControlState.ActivationResult.ACTIVATED,
            state.activate("control-1", IP, ControlActionType.DENY, NOW.plusSeconds(60), NOW));
        assertEquals(IpControlState.ActivationResult.IDEMPOTENT_REPLAY,
            state.activate("control-1", IP, ControlActionType.RATE_LIMIT, NOW.plusSeconds(120), NOW.plusSeconds(1)));

        assertEquals(IpControlDecision.denied(), state.check(IP, NOW.plusSeconds(1)));
        assertEquals(IpControlDecision.allowed(), state.check(IP, NOW.plusSeconds(61)));
    }

    @Test
    void recognizesAnIdempotentReplayBeforeApplyingTheCapacityLimit() {
        LocalIpControlState state = state(1, 2, Duration.ofSeconds(10));

        state.activate("control-1", IP, ControlActionType.DENY, NOW.plusSeconds(60), NOW);

        assertEquals(IpControlState.ActivationResult.IDEMPOTENT_REPLAY,
            state.activate("control-1", "203.0.113.11", ControlActionType.RATE_LIMIT,
                NOW.plusSeconds(120), NOW.plusSeconds(1)));
        assertEquals(IpControlDecision.denied(), state.check(IP, NOW.plusSeconds(1)));
        assertEquals(IpControlDecision.allowed(), state.check("203.0.113.11", NOW.plusSeconds(1)));
    }

    @Test
    void keepsMultipleControlsForOneIpUntilEachControlExpires() {
        LocalIpControlState state = state(4, 2, Duration.ofSeconds(10));

        state.activate("rate-1", IP, ControlActionType.RATE_LIMIT, NOW.plusSeconds(30), NOW);
        state.activate("deny-1", IP, ControlActionType.DENY, NOW.plusSeconds(60), NOW);

        assertEquals(IpControlDecision.denied(), state.check(IP, NOW));
        assertEquals(IpControlDecision.denied(), state.check(IP, NOW.plusSeconds(31)));
        assertEquals(IpControlDecision.allowed(), state.check(IP, NOW.plusSeconds(61)));
    }

    @Test
    void rejectsActivationAtCapacityWithoutEvictingActiveControls() {
        LocalIpControlState state = state(1, 2, Duration.ofSeconds(10));

        assertEquals(IpControlState.ActivationResult.ACTIVATED,
            state.activate("deny-1", IP, ControlActionType.DENY, NOW.plusSeconds(60), NOW));
        assertEquals(IpControlState.ActivationResult.CAPACITY_REJECTED,
            state.activate("deny-2", "203.0.113.11", ControlActionType.DENY, NOW.plusSeconds(60), NOW));

        assertEquals(IpControlDecision.denied(), state.check(IP, NOW));
        assertEquals(IpControlDecision.allowed(), state.check("203.0.113.11", NOW));
    }

    @Test
    void releasesExpiredCapacityAndRejectsAlreadyExpiredActivations() {
        LocalIpControlState state = state(1, 2, Duration.ofSeconds(10));

        state.activate("deny-1", IP, ControlActionType.DENY, NOW.plusSeconds(10), NOW);

        assertEquals(IpControlState.ActivationResult.ACTIVATED,
            state.activate("deny-2", "203.0.113.11", ControlActionType.DENY,
                NOW.plusSeconds(60), NOW.plusSeconds(11)));
        assertEquals(IpControlDecision.allowed(), state.check(IP, NOW.plusSeconds(11)));
        assertEquals(IpControlState.ActivationResult.EXPIRED,
            state.activate("expired", IP, ControlActionType.DENY, NOW, NOW));
    }

    @Test
    void releasesAnExpiredKeyBecauseDurableReplayProtectionBelongsToTheControlRepository() {
        LocalIpControlState state = state(1, 2, Duration.ofSeconds(10));
        state.activate("deny-1", IP, ControlActionType.DENY, NOW.plusSeconds(10), NOW);

        assertEquals(IpControlState.ActivationResult.ACTIVATED,
            state.activate("deny-1", IP, ControlActionType.DENY,
                NOW.plusSeconds(30), NOW.plusSeconds(11)));
        assertEquals(IpControlDecision.denied(), state.check(IP, NOW.plusSeconds(11)));
    }

    @Test
    void expiresAControlAtItsExactExpiryInstant() {
        LocalIpControlState state = state(1, 2, Duration.ofSeconds(10));
        state.activate("deny-1", IP, ControlActionType.DENY, NOW.plusSeconds(10), NOW);

        assertEquals(IpControlDecision.denied(), state.check(IP, NOW.plusSeconds(9)));
        assertEquals(IpControlDecision.allowed(), state.check(IP, NOW.plusSeconds(10)));
    }

    @Test
    void rateLimitsUsingAFixedWindowAndReturnsTheDeterministicRetryTime() {
        LocalIpControlState state = state(4, 2, Duration.ofSeconds(10));
        state.activate("rate-1", IP, ControlActionType.RATE_LIMIT, NOW.plusSeconds(60), NOW);

        assertEquals(IpControlDecision.allowed(), state.check(IP, NOW));
        assertEquals(IpControlDecision.allowed(), state.check(IP, NOW.plusSeconds(1)));

        IpControlDecision limited = state.check(IP, NOW.plusSeconds(2));

        assertTrue(limited.isRateLimited());
        assertFalse(limited.isDenied());
        assertEquals(Duration.ofSeconds(8), limited.getRetryAfter());
        assertEquals(IpControlDecision.allowed(), state.check(IP, NOW.plusSeconds(10)));
    }

    @Test
    void multipleRateLimitControlsForOneIpShareOneFixedWindowCounter() {
        LocalIpControlState state = state(4, 2, Duration.ofSeconds(10));
        state.activate("rate-1", IP, ControlActionType.RATE_LIMIT, NOW.plusSeconds(30), NOW);
        state.activate("rate-2", IP, ControlActionType.RATE_LIMIT, NOW.plusSeconds(60), NOW);

        assertEquals(IpControlDecision.allowed(), state.check(IP, NOW));
        assertEquals(IpControlDecision.allowed(), state.check(IP, NOW.plusSeconds(1)));
        assertEquals(IpControlDecision.rateLimited(Duration.ofSeconds(8)),
            state.check(IP, NOW.plusSeconds(2)));

        assertEquals(IpControlDecision.allowed(), state.check(IP, NOW.plusSeconds(31)));
        assertEquals(IpControlDecision.allowed(), state.check(IP, NOW.plusSeconds(32)));
        assertEquals(IpControlDecision.rateLimited(Duration.ofSeconds(8)),
            state.check(IP, NOW.plusSeconds(33)));
    }

    @Test
    void decisionsExposeOnlyTheEnforcementOutcomeAndRetryDuration() {
        IpControlDecision allowed = IpControlDecision.allowed();
        IpControlDecision denied = IpControlDecision.denied();

        assertFalse(allowed.isDenied());
        assertFalse(allowed.isRateLimited());
        assertNull(allowed.getRetryAfter());
        assertTrue(denied.isDenied());
        assertFalse(denied.isRateLimited());
        assertNull(denied.getRetryAfter());
    }

    private static LocalIpControlState state(int capacity, int permitsPerWindow, Duration fixedWindow) {
        return new LocalIpControlState(capacity, permitsPerWindow, fixedWindow);
    }
}
