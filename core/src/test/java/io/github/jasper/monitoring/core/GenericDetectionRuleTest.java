package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.core.domain.rule.EventConditionRule;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.rule.WindowAggregateRule;
import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import io.github.jasper.monitoring.core.domain.rule.RuleEvaluationContext;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionFailurePolicy;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.rule.RuleDefinition;
import io.github.jasper.monitoring.api.rule.RuleMode;
import io.github.jasper.monitoring.api.rule.RuleSource;
import io.github.jasper.monitoring.api.rule.RuleType;
import io.github.jasper.monitoring.core.domain.RuleMatch;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GenericDetectionRuleTest {
    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

    @Test
    void countsTheCurrentEventAndHonorsTheInclusiveWindowBoundary() {
        WindowAggregateRule<TestRule> rule = new WindowAggregateRule<TestRule>(
            definition("AUTH-01", Duration.ofMinutes(5), 2, RiskLevel.MEDIUM, ControlActionType.RATE_LIMIT),
            event -> event.getEventType() == SecurityEventType.LOGIN_FAILURE,
            event -> event.getEventType() == SecurityEventType.LOGIN_FAILURE,
            WindowAggregateRule.Scope.USER, WindowAggregateRule.Aggregation.EVENT_COUNT, "repeated failures");
        SecurityEvent current = event(SecurityEventType.LOGIN_FAILURE, "alice", null, null, NOW, Collections.<String, String>emptyMap());

        assertFalse(evaluate(rule, current, Arrays.asList(
            event(SecurityEventType.LOGIN_FAILURE, "alice", null, null, NOW.minusSeconds(301), Collections.<String, String>emptyMap()),
            current)).isPresent());
        assertTrue(evaluate(rule, current, Arrays.asList(
            event(SecurityEventType.LOGIN_FAILURE, "alice", null, null, NOW.minusSeconds(300), Collections.<String, String>emptyMap()),
            current)).isPresent());
    }

    @Test
    void appendixBTc06AggregatesOneHundredDistinctResourcesOnlyWhenTheConfiguredAttributeConditionIsMet() {
        WindowAggregateRule<TestRule> rule = new WindowAggregateRule<TestRule>(
            definition("AUTHZ-02", Duration.ofMinutes(10), 100, RiskLevel.HIGH, ControlActionType.DENY),
            event -> "true".equals(event.getAttribute("sequential_access")), event -> event.getResourceId() != null,
            WindowAggregateRule.Scope.SESSION_OR_USER,
            WindowAggregateRule.Aggregation.DISTINCT_RESOURCE_COUNT, "sequential access");
        List<SecurityEvent> history = new ArrayList<SecurityEvent>();
        for (int index = 0; index < 98; index++) {
            history.add(event(SecurityEventType.QUERY, "alice", "session-1", "resource-" + index, NOW.minusSeconds(1),
                Collections.<String, String>emptyMap()));
        }
        history.add(event(SecurityEventType.QUERY, "alice", "session-1", "resource-0", NOW.minusSeconds(1),
            Collections.<String, String>emptyMap()));
        SecurityEvent almost = event(SecurityEventType.QUERY, "alice", "session-1", "resource-98", NOW,
            Collections.singletonMap("sequential_access", "true"));
        history.add(almost);
        SecurityEvent current = event(SecurityEventType.QUERY, "alice", "session-1", "resource-99", NOW,
            Collections.singletonMap("sequential_access", "true"));
        history.add(current);

        assertFalse(evaluate(rule, almost, history.subList(0, history.size() - 1)).isPresent());
        assertTrue(evaluate(rule, current, history).isPresent());
        assertFalse(evaluate(rule, event(SecurityEventType.QUERY, "alice", "session-1", "resource-99", NOW,
            Collections.<String, String>emptyMap()), history).isPresent());
    }

    @Test
    void appendixBTc10MatchesAUserSelfAuthorizationConditionUsingNormalizedAttributes() {
        EventConditionRule<TestRule> rule = new EventConditionRule<TestRule>(
            definition("PRIV-01", Duration.ZERO, 1, RiskLevel.HIGH, ControlActionType.DENY),
            event -> event.getEventType() == SecurityEventType.ROLE_GRANT
            && event.getUserId() != null && event.getUserId().equals(event.getAttribute("target_user_id"))
            && "true".equals(event.getAttribute("privilege_increase")), "self privilege escalation");
        Map<String, String> selfGrant = new HashMap<String, String>();
        selfGrant.put("target_user_id", "alice");
        selfGrant.put("privilege_increase", "true");

        assertTrue(evaluate(rule, event(SecurityEventType.ROLE_GRANT, "alice", null, null, NOW,
            selfGrant), Collections.<SecurityEvent>emptyList()).isPresent());
        assertFalse(evaluate(rule, event(SecurityEventType.ROLE_GRANT, "alice", null, null, NOW,
            Collections.<String, String>emptyMap()), Collections.<SecurityEvent>emptyList()).isPresent());
    }

    @Test
    void dataCountAggregationIgnoresUnknownCandidatesButCountsExplicitZero() {
        WindowAggregateRule<TestRule> rule = dataCountRule(1);
        SecurityEvent unknown = event(SecurityEventType.QUERY, "alice", null, null, NOW,
            Collections.<String, String>emptyMap());
        SecurityEvent explicitZero = dataCountEvent(0L, NOW);

        assertFalse(evaluate(rule, unknown, Collections.singletonList(unknown)).isPresent());
        assertTrue(evaluate(rule, explicitZero, Collections.singletonList(explicitZero)).isPresent());
    }

    @Test
    void dataCountAggregationSaturatesKnownValuesInsteadOfOverflowing() {
        WindowAggregateRule<TestRule> rule = dataCountRule(Long.MAX_VALUE);
        SecurityEvent prior = dataCountEvent(1L, NOW.minusSeconds(1));
        SecurityEvent current = dataCountEvent(Long.MAX_VALUE, NOW);

        assertTrue(evaluate(rule, current, Arrays.asList(prior, current)).isPresent());
    }

    @Test
    void distinctResourceAggregationIgnoresBlankAndWhitespaceResourceIds() {
        WindowAggregateRule<TestRule> rule = new WindowAggregateRule<TestRule>(
            definition("DATA-02", Duration.ofMinutes(5), 2, RiskLevel.HIGH, ControlActionType.DENY),
            event -> event.getEventType() == SecurityEventType.QUERY,
            event -> event.getEventType() == SecurityEventType.QUERY,
            WindowAggregateRule.Scope.USER,
            WindowAggregateRule.Aggregation.DISTINCT_RESOURCE_COUNT, "distinct resources");
        SecurityEvent current = event(SecurityEventType.QUERY, "alice", null, "resource-1", NOW,
            Collections.<String, String>emptyMap());
        SecurityEvent blank = event(SecurityEventType.QUERY, "alice", null, "", NOW,
            Collections.<String, String>emptyMap());
        SecurityEvent whitespace = event(SecurityEventType.QUERY, "alice", null, "   ", NOW,
            Collections.<String, String>emptyMap());

        assertFalse(evaluate(rule, current, Arrays.asList(current, blank, whitespace)).isPresent());
    }

    private static WindowAggregateRule<TestRule> dataCountRule(long threshold) {
        return new WindowAggregateRule<TestRule>(
            definition("DATA-03", Duration.ofMinutes(5), threshold, RiskLevel.MEDIUM,
                ControlActionType.REQUIRE_MFA),
            event -> event.getEventType() == SecurityEventType.QUERY,
            event -> event.getEventType() == SecurityEventType.QUERY,
            WindowAggregateRule.Scope.USER, WindowAggregateRule.Aggregation.DATA_COUNT, "data count");
    }

    private static RuleDefinition<TestRule> definition(String id, Duration window, long threshold,
            RiskLevel risk, ControlActionType... controls) {
        RuleDefinition.Builder<TestRule> builder = RuleDefinition.builder(TestRule.class, id)
            .appliesTo(TestAction.class).historyWindow(window).threshold(threshold).risk(risk)
            .mode(RuleMode.OBSERVE).source(RuleSource.INTERNAL);
        for (ControlActionType control : controls) {
            builder.control(control);
        }
        return builder.build();
    }

    private static Optional<RuleMatch> evaluate(DetectionRule<TestRule> rule, SecurityEvent event,
            List<SecurityEvent> history) {
        ActionDefinition action = ActionDefinition.builder("test:action")
            .eventType(event.getEventType()).resourceType("test")
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
        return RuleEvaluationContext.builder(event, TestAction.class, action)
            .history(history).build().evaluate(rule).getMatch();
    }

    static final class TestAction implements ActionType {
    }

    static final class TestRule implements RuleType {
    }

    private static SecurityEvent dataCountEvent(long dataCount, Instant occurredAt) {
        return SecurityEvent.builder()
            .eventType(SecurityEventType.QUERY)
            .userId("alice")
            .sourceIp("203.0.113.8")
            .occurredAt(occurredAt)
            .dataCount(dataCount)
            .build();
    }

    private SecurityEvent event(SecurityEventType type, String userId, String sessionId, String resourceId, Instant occurredAt,
                                Map<String, String> attributes) {
        return SecurityEvent.builder()
            .eventType(type)
            .userId(userId)
            .sourceIp("203.0.113.8")
            .sessionIdHash(sessionId)
            .resourceType(resourceId == null ? null : "document")
            .resourceId(resourceId)
            .occurredAt(occurredAt)
            .attributes(attributes)
            .build();
    }
}
