package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.core.domain.rule.EventConditionRule;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.rule.WindowAggregateRule;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.SecurityEventType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenericDetectionRuleTest {
    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

    @Test
    void countsTheCurrentEventAndHonorsTheInclusiveWindowBoundary() {
        WindowAggregateRule rule = new WindowAggregateRule("AUTH-01",
            event -> event.getEventType() == SecurityEventType.LOGIN_FAILURE,
            event -> event.getEventType() == SecurityEventType.LOGIN_FAILURE,
            Duration.ofMinutes(5), 2, WindowAggregateRule.Scope.USER,
            WindowAggregateRule.Aggregation.EVENT_COUNT, RiskLevel.MEDIUM,
            Collections.singletonList(ControlActionType.RATE_LIMIT), "repeated failures");
        SecurityEvent current = event(SecurityEventType.LOGIN_FAILURE, "alice", null, null, NOW, Collections.<String, String>emptyMap());

        assertFalse(rule.evaluate(current, Arrays.asList(
            event(SecurityEventType.LOGIN_FAILURE, "alice", null, null, NOW.minusSeconds(301), Collections.<String, String>emptyMap()),
            current)).isPresent());
        assertTrue(rule.evaluate(current, Arrays.asList(
            event(SecurityEventType.LOGIN_FAILURE, "alice", null, null, NOW.minusSeconds(300), Collections.<String, String>emptyMap()),
            current)).isPresent());
    }

    @Test
    void appendixBTc06AggregatesOneHundredDistinctResourcesOnlyWhenTheConfiguredAttributeConditionIsMet() {
        WindowAggregateRule rule = new WindowAggregateRule("AUTHZ-02",
            event -> "true".equals(event.getAttribute("sequential_access")), event -> event.getResourceId() != null,
            Duration.ofMinutes(10), 100, WindowAggregateRule.Scope.SESSION_OR_USER,
            WindowAggregateRule.Aggregation.DISTINCT_RESOURCE_COUNT, RiskLevel.HIGH,
            Collections.singletonList(ControlActionType.DENY), "sequential access");
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

        assertFalse(rule.evaluate(almost, history.subList(0, history.size() - 1)).isPresent());
        assertTrue(rule.evaluate(current, history).isPresent());
        assertFalse(rule.evaluate(event(SecurityEventType.QUERY, "alice", "session-1", "resource-99", NOW,
            Collections.<String, String>emptyMap()), history).isPresent());
    }

    @Test
    void appendixBTc10MatchesAUserSelfAuthorizationConditionUsingNormalizedAttributes() {
        EventConditionRule rule = new EventConditionRule("PRIV-01", event -> event.getEventType() == SecurityEventType.ROLE_GRANT
            && event.getUserId() != null && event.getUserId().equals(event.getAttribute("target_user_id"))
            && "true".equals(event.getAttribute("privilege_increase")), RiskLevel.HIGH,
            Collections.singletonList(ControlActionType.DENY), "self privilege escalation");
        Map<String, String> selfGrant = new HashMap<String, String>();
        selfGrant.put("target_user_id", "alice");
        selfGrant.put("privilege_increase", "true");

        assertTrue(rule.evaluate(event(SecurityEventType.ROLE_GRANT, "alice", null, null, NOW,
            selfGrant), Collections.<SecurityEvent>emptyList()).isPresent());
        assertFalse(rule.evaluate(event(SecurityEventType.ROLE_GRANT, "alice", null, null, NOW,
            Collections.<String, String>emptyMap()), Collections.<SecurityEvent>emptyList()).isPresent());
    }

    @Test
    void dataCountAggregationIgnoresUnknownCandidatesButCountsExplicitZero() {
        WindowAggregateRule rule = dataCountRule(1);
        SecurityEvent unknown = event(SecurityEventType.QUERY, "alice", null, null, NOW,
            Collections.<String, String>emptyMap());
        SecurityEvent explicitZero = dataCountEvent(0L, NOW);

        assertFalse(rule.evaluate(unknown, Collections.singletonList(unknown)).isPresent());
        assertTrue(rule.evaluate(explicitZero, Collections.singletonList(explicitZero)).isPresent());
    }

    @Test
    void dataCountAggregationSaturatesKnownValuesInsteadOfOverflowing() {
        WindowAggregateRule rule = dataCountRule(Long.MAX_VALUE);
        SecurityEvent prior = dataCountEvent(1L, NOW.minusSeconds(1));
        SecurityEvent current = dataCountEvent(Long.MAX_VALUE, NOW);

        assertTrue(rule.evaluate(current, Arrays.asList(prior, current)).isPresent());
    }

    private static WindowAggregateRule dataCountRule(long threshold) {
        return new WindowAggregateRule("DATA-03",
            event -> event.getEventType() == SecurityEventType.QUERY,
            event -> event.getEventType() == SecurityEventType.QUERY,
            Duration.ofMinutes(5), threshold, WindowAggregateRule.Scope.USER,
            WindowAggregateRule.Aggregation.DATA_COUNT, RiskLevel.MEDIUM,
            Collections.singletonList(ControlActionType.REQUIRE_MFA), "data count");
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
