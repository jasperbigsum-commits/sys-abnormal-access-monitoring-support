package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.core.domain.RuleMatch;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.rule.DefaultRuleCatalog;
import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.jasper.monitoring.api.SecurityEventType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultRuleCatalogAuthenticationTest {
    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");
    private static final String SOURCE_IP = "203.0.113.8";

    @Test
    void authOneDoesNotAggregateAnonymousFailuresForDifferentAttemptedAccounts() {
        List<SecurityEvent> history = new ArrayList<SecurityEvent>();
        for (int index = 0; index < 5; index++) {
            history.add(loginFailure("account-hash-" + index));
        }

        assertFalse(rule("AUTH-01").evaluate(history.get(4), history).isPresent());
    }

    @Test
    void authOneAggregatesAnonymousFailuresForTheSameAttemptedAccount() {
        List<SecurityEvent> history = new ArrayList<SecurityEvent>();
        for (int index = 0; index < 5; index++) {
            history.add(loginFailure("account-hash"));
        }

        Optional<RuleMatch> match = rule("AUTH-01").evaluate(history.get(4), history);

        assertTrue(match.isPresent());
        assertEquals("attempted:account-hash", match.get().getSubject());
        assertEquals(Duration.ofMinutes(15), match.get().getControlTtl());
    }

    @Test
    void authOneFallsBackToTheEventSubjectWhenTheAttemptedAccountHashIsMissing() {
        List<SecurityEvent> history = new ArrayList<SecurityEvent>();
        for (int index = 0; index < 5; index++) {
            history.add(loginFailure(null));
        }

        Optional<RuleMatch> match = rule("AUTH-01").evaluate(history.get(4), history);

        assertTrue(match.isPresent());
        assertEquals(SOURCE_IP, match.get().getSubject());
    }

    @Test
    void authTwoMatchesTenDistinctAttemptedAccountsFromOneIp() {
        List<SecurityEvent> history = new ArrayList<SecurityEvent>();
        for (int index = 0; index < 10; index++) {
            history.add(loginFailure("account-hash-" + index));
        }

        Optional<RuleMatch> match = rule("AUTH-02").evaluate(history.get(9), history);

        assertTrue(match.isPresent());
        assertEquals("ip:" + SOURCE_IP, match.get().getSubject());
    }

    @Test
    void authTwoRequiresAtLeastEightyPercentFailuresForTheIpWindow() {
        List<SecurityEvent> history = new ArrayList<SecurityEvent>();
        for (int index = 0; index < 10; index++) {
            history.add(loginFailure("account-hash-" + index));
        }
        for (int index = 0; index < 3; index++) {
            history.add(loginSuccess());
        }

        assertFalse(rule("AUTH-02").evaluate(history.get(9), history).isPresent());
    }

    private static DetectionRule rule(String ruleId) {
        for (DetectionRule rule : DefaultRuleCatalog.initialRules()) {
            if (ruleId.equals(rule.getRuleId())) {
                return rule;
            }
        }
        throw new AssertionError("Missing rule " + ruleId);
    }

    private static SecurityEvent loginFailure(String attemptedAccountHash) {
        return event(SecurityEventType.LOGIN_FAILURE, attemptedAccountHash);
    }

    private static SecurityEvent loginSuccess() {
        return event(SecurityEventType.LOGIN_SUCCESS, null);
    }

    private static SecurityEvent event(SecurityEventType eventType, String attemptedAccountHash) {
        return SecurityEvent.builder()
            .eventType(eventType)
            .occurredAt(NOW)
            .sourceIp(SOURCE_IP)
            .attributes(attemptedAccountHash == null ? Collections.<String, String>emptyMap()
                : Collections.singletonMap("attempted_account_hash", attemptedAccountHash))
            .build();
    }
}
