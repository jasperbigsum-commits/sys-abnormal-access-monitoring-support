package io.github.jasper.monitoring.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.EventFactSource;
import io.github.jasper.monitoring.api.EventInputIssue;
import io.github.jasper.monitoring.api.EventInputStatus;
import io.github.jasper.monitoring.api.EventInputValidation;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class SecurityEventTest {

    @Test
    void preservesInputQualityAndKnownFactFlagsOnAcceptedEvent() {
        EventInputIssue issue = EventInputIssue.missing("EXPT-01", "dataCount", EventFactSource.SERVER_COMPUTED);
        EventInputValidation validation = EventInputValidation.incomplete(
            Collections.singletonList(issue), Collections.singleton("EXPT-01"));

        SecurityEvent event = SecurityEvent.from(explicitZeroDraft(), "test", "event-1", Instant.EPOCH, validation);

        assertEquals(EventInputStatus.INCOMPLETE, event.getInputStatus());
        assertEquals(Collections.singletonList(issue), event.getInputIssues());
        assertTrue(event.hasDataCount());
    }

    @Test
    void treatsTheLegacyFactoryInputAsValid() {
        SecurityEvent event = SecurityEvent.from(explicitZeroDraft(), "test", "event-1", Instant.EPOCH);

        assertEquals(EventInputStatus.VALID, event.getInputStatus());
        assertTrue(event.getInputIssues().isEmpty());
    }

    @Test
    void rejectsMissingInputValidationWithAStableCode() {
        MonitoringValidationException exception = assertThrows(MonitoringValidationException.class,
            () -> SecurityEvent.from(explicitZeroDraft(), "test", "event-1", Instant.EPOCH, null));

        assertEquals(MonitoringErrorCode.REQUIRED_FIELD_MISSING, exception.getErrorCode());
    }

    @Test
    void rejectsBuilderInputQualityCombinationsThatCannotBeValidated() {
        EventInputIssue issue = EventInputIssue.missing("EXPT-01", "dataCount", EventFactSource.SERVER_COMPUTED);

        assertThrows(IllegalArgumentException.class,
            () -> SecurityEvent.builder().inputStatus(EventInputStatus.INCOMPLETE).build());
        assertThrows(IllegalArgumentException.class,
            () -> SecurityEvent.builder().inputStatus(EventInputStatus.VALID)
                .inputIssues(Collections.singletonList(issue)).build());
        assertThrows(IllegalArgumentException.class,
            () -> SecurityEvent.builder().inputStatus(EventInputStatus.UNKNOWN)
                .inputIssues(Collections.singletonList(issue)).build());
        assertThrows(IllegalArgumentException.class,
            () -> SecurityEvent.builder().inputStatus(EventInputStatus.INCOMPLETE)
                .inputIssues(Arrays.asList((EventInputIssue) null)).build());
    }

    @Test
    void preservesBuilderSupportForValidLegacyAndIncompleteRows() {
        EventInputIssue issue = EventInputIssue.missing("EXPT-01", "dataCount", EventFactSource.SERVER_COMPUTED);

        SecurityEvent legacyEvent = SecurityEvent.builder().build();
        SecurityEvent incompleteEvent = SecurityEvent.builder().inputStatus(EventInputStatus.INCOMPLETE)
            .inputIssues(Collections.singletonList(issue)).build();

        assertEquals(EventInputStatus.UNKNOWN, legacyEvent.getInputStatus());
        assertEquals(EventInputStatus.INCOMPLETE, incompleteEvent.getInputStatus());
        assertEquals(Collections.singletonList(issue), incompleteEvent.getInputIssues());
    }

    private static SecurityEventDraft explicitZeroDraft() {
        return SecurityEventDraft.builder()
            .eventType(SecurityEventType.EXPORT)
            .action("EXPORT")
            .result(SecurityEventResult.SUCCESS)
            .sourceIp("203.0.113.9")
            .requestId("request-1")
            .dataCount(0L)
            .occurredAt(Instant.EPOCH)
            .build();
    }
}
