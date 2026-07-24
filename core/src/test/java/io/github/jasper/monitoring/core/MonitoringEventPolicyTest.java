package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.EventInputIssue;
import io.github.jasper.monitoring.api.EventInputStatus;
import io.github.jasper.monitoring.api.EventInputValidation;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.core.application.quality.DefaultMonitoringEventPolicy;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitoringEventPolicyTest {
    private static final Set<String> ENABLED_RULE_IDS = new LinkedHashSet<String>(Arrays.asList(
        "AUTH-02", "SESS-01", "AUTHZ-02", "DATA-02", "DATA-03", "EXPT-01", "EXPT-02",
        "PRIV-01", "PRIV-02", "SECU-01", "CUSTOM-01"));

    private final DefaultMonitoringEventPolicy policy = new DefaultMonitoringEventPolicy();

    @Test
    void excludesOnlyExportRulesWhenExportDataCountIsUnknown() {
        EventInputValidation validation = policy.validate(exportDraftWithoutDataCount(), ENABLED_RULE_IDS);

        assertEquals(EventInputStatus.INCOMPLETE, validation.getStatus());
        assertFalse(validation.isEligible("EXPT-01"));
        assertFalse(validation.isEligible("EXPT-02"));
        assertTrue(validation.isEligible("SECU-01"));
        assertTrue(validation.isEligible("CUSTOM-01"));
        assertEquals(2, validation.getIssues().size());
        for (EventInputIssue issue : validation.getIssues()) {
            assertEquals("MISSING_DATA_COUNT", issue.getIssueCode());
            assertEquals("dataCount", issue.getFactName());
        }
    }

    @Test
    void reportsInvalidIpWithoutKeepingTheUntrustedValue() {
        String invalidIp = "invalid-host.example";
        SecurityEventDraft draft = baseDraft(SecurityEventType.LOGIN_FAILURE)
            .sourceIp(invalidIp)
            .build();

        EventInputValidation validation = policy.validate(draft, ENABLED_RULE_IDS);

        assertEquals(EventInputStatus.INCOMPLETE, validation.getStatus());
        assertFalse(validation.isEligible("AUTH-02"));
        assertTrue(validation.isEligible("SECU-01"));
        assertEquals(1, validation.getIssues().size());
        assertEquals("INVALID_SOURCE_IP", validation.getIssues().get(0).getIssueCode());
        assertFalse(validation.getIssues().get(0).toString().contains(invalidIp));
    }

    @Test
    void ignoresAbsentOptionalBooleanConditionsButRejectsPresentNonCanonicalValues() {
        EventInputValidation absent = policy.validate(baseDraft(SecurityEventType.SESSION_CONCURRENT)
            .dataCount(3)
            .build(), ENABLED_RULE_IDS);

        EventInputValidation invalid = policy.validate(baseDraft(SecurityEventType.SESSION_CONCURRENT)
            .dataCount(3)
            .attribute("different_networks", "TRUE")
            .build(), ENABLED_RULE_IDS);

        assertEquals(EventInputStatus.VALID, absent.getStatus());
        assertTrue(absent.isEligible("SESS-01"));
        assertEquals(EventInputStatus.INCOMPLETE, invalid.getStatus());
        assertFalse(invalid.isEligible("SESS-01"));
        assertEquals("INVALID_FACT", invalid.getIssues().get(0).getIssueCode());
    }

    @Test
    void excludesSessionRuleWhenItsNetworkConditionIsTrueButDataCountIsUnknown() {
        EventInputValidation validation = policy.validate(baseDraft(SecurityEventType.SESSION_CONCURRENT)
            .attribute("different_networks", "true")
            .build(), ENABLED_RULE_IDS);

        assertEquals(EventInputStatus.INCOMPLETE, validation.getStatus());
        assertFalse(validation.isEligible("SESS-01"));
        assertTrue(validation.isEligible("DATA-03"));
        assertTrue(validation.isEligible("SECU-01"));
        assertEquals(1, validation.getIssues().size());
        assertEquals("MISSING_DATA_COUNT", validation.getIssues().get(0).getIssueCode());
    }

    @Test
    void excludesSensitiveDataRuleWhenOutsideHoursConditionIsTrueButDataCountIsUnknown() {
        EventInputValidation validation = policy.validate(baseDraft(SecurityEventType.LOGIN_SUCCESS)
            .attribute("sensitive", "true")
            .attribute("work_hours", "false")
            .build(), ENABLED_RULE_IDS);

        assertEquals(EventInputStatus.INCOMPLETE, validation.getStatus());
        assertFalse(validation.isEligible("DATA-03"));
        assertTrue(validation.isEligible("SESS-01"));
        assertTrue(validation.isEligible("SECU-01"));
        assertEquals(1, validation.getIssues().size());
        assertEquals("MISSING_DATA_COUNT", validation.getIssues().get(0).getIssueCode());
    }

    @Test
    void keepsCountRulesEligibleWhenTheirOptionalBooleanConditionsAreAbsentOrFalse() {
        EventInputValidation sessionAbsent = policy.validate(baseDraft(SecurityEventType.SESSION_CONCURRENT).build(),
            ENABLED_RULE_IDS);
        EventInputValidation sensitiveFalse = policy.validate(baseDraft(SecurityEventType.LOGIN_SUCCESS)
            .attribute("sensitive", "false")
            .attribute("work_hours", "false")
            .build(), ENABLED_RULE_IDS);

        assertTrue(sessionAbsent.isEligible("SESS-01"));
        assertTrue(sensitiveFalse.isEligible("DATA-03"));
        assertEquals(EventInputStatus.VALID, sessionAbsent.getStatus());
        assertEquals(EventInputStatus.VALID, sensitiveFalse.getStatus());
    }

    @Test
    void validatesOnlyEnabledBuiltInRules() {
        EventInputValidation validation = policy.validate(exportDraftWithoutDataCount(),
            new LinkedHashSet<String>(Arrays.asList("SECU-01", "CUSTOM-01")));

        assertEquals(EventInputStatus.VALID, validation.getStatus());
        assertTrue(validation.isEligible("SECU-01"));
        assertTrue(validation.isEligible("CUSTOM-01"));
    }

    @Test
    void excludesOnlyExportBaselineRuleForANonFiniteRatio() {
        EventInputValidation validation = policy.validate(baseDraft(SecurityEventType.EXPORT)
            .dataCount(1)
            .attribute("baseline_ratio", "Infinity")
            .build(), ENABLED_RULE_IDS);

        assertEquals(EventInputStatus.INCOMPLETE, validation.getStatus());
        assertTrue(validation.isEligible("EXPT-01"));
        assertFalse(validation.isEligible("EXPT-02"));
        assertEquals("INVALID_FACT", validation.getIssues().get(0).getIssueCode());
        assertFalse(validation.getIssues().get(0).toString().contains("Infinity"));
    }

    @Test
    void requiresTargetFactsForRoleGrantWithoutMakingOptionalPrivilegeFlagsMandatory() {
        EventInputValidation validation = policy.validate(baseDraft(SecurityEventType.ROLE_GRANT).build(),
            ENABLED_RULE_IDS);

        assertEquals(EventInputStatus.INCOMPLETE, validation.getStatus());
        assertFalse(validation.isEligible("PRIV-01"));
        assertTrue(validation.isEligible("PRIV-02"));
        assertEquals("MISSING_TARGET_USER_ID", validation.getIssues().get(0).getIssueCode());
    }

    @Test
    void requiresResourceIdsOnlyWhenDistinctResourceRulesCanEvaluate() {
        EventInputValidation query = policy.validate(baseDraft(SecurityEventType.QUERY).build(), ENABLED_RULE_IDS);
        EventInputValidation unrelated = policy.validate(baseDraft(SecurityEventType.LOGIN_SUCCESS).build(), ENABLED_RULE_IDS);

        assertFalse(query.isEligible("DATA-02"));
        assertTrue(query.isEligible("AUTHZ-02"));
        assertEquals(EventInputStatus.VALID, unrelated.getStatus());
        assertTrue(unrelated.isEligible("DATA-02"));
    }

    private static SecurityEventDraft exportDraftWithoutDataCount() {
        return baseDraft(SecurityEventType.EXPORT)
            .attribute("sensitivity", "HIGH")
            .build();
    }

    private static SecurityEventDraft.Builder baseDraft(SecurityEventType eventType) {
        return SecurityEventDraft.builder()
            .eventType(eventType)
            .action(eventType.name())
            .result(SecurityEventResult.SUCCESS)
            .sourceIp("203.0.113.8")
            .requestId("request-1")
            .userId("alice")
            .occurredAt(Instant.parse("2026-07-24T00:00:00Z"));
    }
}
