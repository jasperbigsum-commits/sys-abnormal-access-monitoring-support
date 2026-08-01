package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.core.domain.RuleMatch;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.rule.DefaultRuleCatalog;
import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import io.github.jasper.monitoring.core.domain.rule.RuleEvaluationContext;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionFailurePolicy;
import io.github.jasper.monitoring.api.action.ActionType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.core.domain.EventFact;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultRuleCatalogAuthenticationTest {
    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");
    private static final String SOURCE_IP = "203.0.113.8";

    @Test
    void authOneDoesNotAggregateAnonymousFailuresForDifferentLoginSubjects() {
        List<SecurityEvent> history = new ArrayList<SecurityEvent>();
        for (int index = 0; index < 5; index++) {
            history.add(loginFailure("account-hash-" + index));
        }

        assertFalse(evaluate(rule("AUTH-01"), history.get(4), history).isPresent());
    }

    @Test
    void authOneAggregatesAnonymousFailuresForTheSameLoginSubject() {
        List<SecurityEvent> history = new ArrayList<SecurityEvent>();
        for (int index = 0; index < 5; index++) {
            history.add(loginFailure("account-hash"));
        }

        Optional<RuleMatch> match = evaluate(rule("AUTH-01"), history.get(4), history);

        assertTrue(match.isPresent());
        assertEquals("account-hash", match.get().getSubject());
        assertEquals(Duration.ofMinutes(15), match.get().getControlTtl());
    }

    @Test
    void authOneDoesNotGuessASubjectWhenTheTypedFactIsMissing() {
        List<SecurityEvent> history = new ArrayList<SecurityEvent>();
        for (int index = 0; index < 5; index++) {
            history.add(loginFailure(null));
        }

        Optional<RuleMatch> match = evaluate(rule("AUTH-01"), history.get(4), history);

        assertFalse(match.isPresent());
    }

    @Test
    void authTwoMatchesTenDistinctLoginSubjectsFromOneIp() {
        List<SecurityEvent> history = new ArrayList<SecurityEvent>();
        for (int index = 0; index < 10; index++) {
            history.add(loginFailure("account-hash-" + index));
        }

        Optional<RuleMatch> match = evaluate(rule("AUTH-02"), history.get(9), history);

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

        assertFalse(evaluate(rule("AUTH-02"), history.get(9), history).isPresent());
    }

    @Test
    void authThreeUsesTheProtectedLoginSubject() {
        SecurityEvent disabled = SecurityEvent.builder()
            .eventType(SecurityEventType.LOGIN_FAILURE)
            .occurredAt(NOW)
            .sourceIp(SOURCE_IP)
            .reasonCode(BuiltInReasonCodes.Authentication.ACCOUNT_DISABLED.getCode())
            .facts(Collections.singletonList(new EventFact(BuiltInFacts.LOGIN_SUBJECT_KEY.getKey(),
                String.class.getName(), "v1:disabled-account", FactSource.FRAMEWORK_OUTCOME)))
            .build();

        Optional<RuleMatch> match = evaluate(rule("AUTH-03"), disabled,
            Collections.singletonList(disabled));

        assertTrue(match.isPresent());
        assertEquals("v1:disabled-account", match.get().getSubject());
    }

    @Test
    void catalogRequiresStrictLowercaseBooleanLiterals() {
        SecurityEvent canonical = concurrentSession("true");
        SecurityEvent upperCase = concurrentSession("TRUE");
        SecurityEvent numeric = concurrentSession("1");

        assertTrue(evaluate(rule("SESS-01"), canonical, Collections.singletonList(canonical)).isPresent());
        assertFalse(evaluate(rule("SESS-01"), upperCase, Collections.singletonList(upperCase)).isPresent());
        assertFalse(evaluate(rule("SESS-01"), numeric, Collections.singletonList(numeric)).isPresent());
    }

    @Test
    void exportTwoSaturatesKnownDailyCountsInsteadOfOverflowing() {
        SecurityEvent earlier = export(Long.MAX_VALUE, NOW);
        SecurityEvent current = export(1L, NOW);

        assertTrue(evaluate(rule("EXPT-02"), current, Arrays.asList(earlier, current)).isPresent());
    }

    private static DetectionRule rule(String ruleId) {
        for (DetectionRule rule : DefaultRuleCatalog.typedRules()) {
            if (ruleId.equals(rule.getRuleId())) {
                return rule;
            }
        }
        throw new AssertionError("Missing rule " + ruleId);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Optional<RuleMatch> evaluate(DetectionRule rule, SecurityEvent event,
            List<SecurityEvent> history) {
        Class<? extends ActionType> actionType =
            (Class<? extends ActionType>) rule.definition().getActionTypes().iterator().next();
        ActionDefinition action = ActionDefinition.builder("test:action")
            .eventType(event.getEventType()).resourceType("test")
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
        return RuleEvaluationContext.builder(event, actionType, action)
            .history(history).build().evaluate(rule).getMatch();
    }

    private static SecurityEvent loginFailure(String attemptedAccountHash) {
        return event(SecurityEventType.LOGIN_FAILURE, attemptedAccountHash);
    }

    private static SecurityEvent loginSuccess() {
        return event(SecurityEventType.LOGIN_SUCCESS, null);
    }

    private static SecurityEvent event(SecurityEventType eventType, String loginSubjectKey) {
        return SecurityEvent.builder()
            .eventType(eventType)
            .occurredAt(NOW)
            .sourceIp(SOURCE_IP)
            .facts(loginSubjectKey == null ? Collections.<EventFact>emptyList()
                : Collections.singletonList(new EventFact(BuiltInFacts.LOGIN_SUBJECT_KEY.getKey(),
                    String.class.getName(), loginSubjectKey, FactSource.FRAMEWORK_OUTCOME)))
            .build();
    }

    private static SecurityEvent concurrentSession(String differentNetworks) {
        return SecurityEvent.builder()
            .eventType(SecurityEventType.SESSION_CONCURRENT)
            .occurredAt(NOW)
            .sourceIp(SOURCE_IP)
            .dataCount(3L)
            .attributes(Collections.singletonMap("different_networks", differentNetworks))
            .build();
    }

    private static SecurityEvent export(long dataCount, Instant occurredAt) {
        return SecurityEvent.builder()
            .eventType(SecurityEventType.EXPORT)
            .occurredAt(occurredAt)
            .sourceIp(SOURCE_IP)
            .userId("alice")
            .result(SecurityEventResult.SUCCESS)
            .dataCount(dataCount)
            .build();
    }
}
