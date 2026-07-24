package io.github.jasper.monitoring.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EventInputValidationTest {

    @Test
    void snapshotsIssuesAndIneligibleRules() {
        EventInputIssue issue = EventInputIssue.missing("EXPT-01", "dataCount", EventFactSource.SERVER_COMPUTED);
        List<EventInputIssue> issues = new ArrayList<EventInputIssue>();
        issues.add(issue);
        Set<String> ineligibleRuleIds = new LinkedHashSet<String>();
        ineligibleRuleIds.add("EXPT-01");

        EventInputValidation validation = EventInputValidation.incomplete(issues, ineligibleRuleIds);
        issues.clear();
        ineligibleRuleIds.clear();

        assertEquals(EventInputStatus.INCOMPLETE, validation.getStatus());
        assertEquals(1, validation.getIssues().size());
        assertFalse(validation.isEligible("EXPT-01"));
        assertTrue(validation.isEligible("SECU-01"));
        assertThrows(UnsupportedOperationException.class, () -> validation.getIssues().add(issue));
        assertThrows(UnsupportedOperationException.class, () -> validation.getIneligibleRuleIds().add("EXPT-02"));
    }

    @Test
    void rejectsBlankIssueIdentifiers() {
        assertThrows(IllegalArgumentException.class,
            () -> EventInputIssue.of(" ", "dataCount", EventInputIssueCode.MISSING_DATA_COUNT,
                EventFactSource.SERVER_COMPUTED));
        assertThrows(IllegalArgumentException.class,
            () -> EventInputIssue.of("EXPT-01", " ", EventInputIssueCode.MISSING_DATA_COUNT,
                EventFactSource.SERVER_COMPUTED));
        assertThrows(IllegalArgumentException.class,
            () -> EventInputIssue.of("EXPT-01", "dataCount", null, EventFactSource.SERVER_COMPUTED));
        assertThrows(IllegalArgumentException.class,
            () -> EventInputIssue.of("EXPT-01", "dataCount", EventInputIssueCode.MISSING_DATA_COUNT, null));
    }

    @Test
    void rejectsUnsafeDiagnosticIdentifiers() {
        assertThrows(IllegalArgumentException.class,
            () -> EventInputIssue.of("EXPT-01", "password", EventInputIssueCode.MISSING_DATA_COUNT,
                EventFactSource.SERVER_COMPUTED));
        assertThrows(IllegalArgumentException.class,
            () -> EventInputIssue.of("EXPT-01", "rawPayload", EventInputIssueCode.MISSING_DATA_COUNT,
                EventFactSource.SERVER_COMPUTED));
        assertThrows(IllegalArgumentException.class,
            () -> EventInputIssue.of("EXPT-01", "data count", EventInputIssueCode.MISSING_DATA_COUNT,
                EventFactSource.SERVER_COMPUTED));
        assertThrows(IllegalArgumentException.class,
            () -> EventInputIssue.of("EXPT-01", "data\u0000Count", EventInputIssueCode.MISSING_DATA_COUNT,
                EventFactSource.SERVER_COMPUTED));
        assertThrows(IllegalArgumentException.class,
            () -> EventInputIssue.of("secret-token", "dataCount", EventInputIssueCode.MISSING_DATA_COUNT,
                EventFactSource.SERVER_COMPUTED));
    }

    @Test
    void exposesOnlyControlledDiagnosticCodesAndSources() {
        assertThrows(NoSuchMethodException.class,
            () -> EventInputIssue.class.getMethod("of", String.class, String.class, String.class, String.class));
        assertThrows(IllegalArgumentException.class,
            () -> EventInputIssueCode.valueOf("RAW_PAYLOAD_ABC123"));
        assertThrows(IllegalArgumentException.class,
            () -> EventFactSource.valueOf("RAW_EXCEPTION"));
    }

    @Test
    void acceptsSafeCustomRuleIdentifiers() {
        EventInputIssue issue = EventInputIssue.missing("custom.rule-01", "dataCount", EventFactSource.SERVER_COMPUTED);
        EventInputValidation validation = EventInputValidation.incomplete(
            Collections.singletonList(issue), Collections.singleton("custom.rule-01"));

        assertFalse(validation.isEligible("custom.rule-01"));
        assertTrue(validation.isEligible("custom.other-rule"));
    }

    @Test
    void rejectsValidationWhenIneligibleRulesDoNotMatchIssueRules() {
        EventInputIssue issue = EventInputIssue.missing("EXPT-01", "dataCount", EventFactSource.SERVER_COMPUTED);

        assertThrows(IllegalArgumentException.class,
            () -> EventInputValidation.incomplete(Collections.singletonList(issue), Collections.singleton("AUTH-01")));
        assertThrows(IllegalArgumentException.class,
            () -> EventInputValidation.of(EventInputStatus.INCOMPLETE,
                Collections.singletonList(issue), Collections.<String>emptySet()));
        assertThrows(IllegalArgumentException.class,
            () -> EventInputValidation.incomplete(Collections.singletonList(issue), Collections.singleton(" EXPT-01")));
    }

    @Test
    void requiresIssuesAndIneligibleRulesToMatchTheInputStatus() {
        EventInputIssue issue = EventInputIssue.missing("EXPT-01", "dataCount", EventFactSource.SERVER_COMPUTED);

        assertThrows(IllegalArgumentException.class,
            () -> EventInputValidation.of(EventInputStatus.INCOMPLETE,
                Collections.<EventInputIssue>emptyList(), Collections.<String>emptySet()));
        assertThrows(IllegalArgumentException.class,
            () -> EventInputValidation.of(EventInputStatus.INVALID,
                Collections.<EventInputIssue>emptyList(), Collections.<String>emptySet()));
        assertThrows(IllegalArgumentException.class,
            () -> EventInputValidation.of(EventInputStatus.VALID,
                Collections.singletonList(issue), Collections.singleton("EXPT-01")));
        assertThrows(IllegalArgumentException.class,
            () -> EventInputValidation.of(EventInputStatus.UNKNOWN,
                Collections.singletonList(issue), Collections.singleton("EXPT-01")));

        assertEquals(EventInputStatus.INVALID, EventInputValidation.of(EventInputStatus.INVALID,
            Collections.singletonList(issue), Collections.singleton("EXPT-01")).getStatus());
        assertEquals(EventInputStatus.UNKNOWN, EventInputValidation.of(EventInputStatus.UNKNOWN,
            Collections.<EventInputIssue>emptyList(), Collections.<String>emptySet()).getStatus());
    }
}
