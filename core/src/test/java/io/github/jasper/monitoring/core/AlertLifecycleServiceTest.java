package io.github.jasper.monitoring.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.jasper.monitoring.api.AlertStatus;
import io.github.jasper.monitoring.api.DispositionType;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AlertLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T01:10:00Z");

    @Test
    void acknowledgesAndClosesWithAppendOnlyHistoryWhilePreservingOriginalEvents() {
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        SecurityEvent event = SecurityEvent.from(SecurityEventDraft.builder()
            .eventType(SecurityEventType.LOGIN_FAILURE)
            .action("LOGIN")
            .result(SecurityEventResult.FAILURE)
            .sourceIp("203.0.113.9")
            .requestId("request-1")
            .userId("alice")
            .occurredAt(NOW.minusSeconds(60))
            .build(), "orders", "event-1", NOW.minusSeconds(30));
        repository.saveEvent(event);
        repository.saveAlert(new SecurityAlert("alert-1", "AUTH-01", RiskLevel.HIGH, "AUTH-01:alice", "alice",
            AlertStatus.NEW, NOW.minusSeconds(60), NOW.minusSeconds(60), 1));

        AlertLifecycleService lifecycle = new AlertLifecycleService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        SecurityAlert acknowledged = lifecycle.acknowledge("alert-1", "operator-1", "Investigating login failures");
        SecurityAlert inProgress = lifecycle.startInvestigation("alert-1", "operator-1", "Assigned to incident queue");
        SecurityAlert closed = lifecycle.close("alert-1", "operator-1", "Confirmed mitigation", "ticket-42");

        assertEquals(AlertStatus.ACKNOWLEDGED, acknowledged.getStatus());
        assertEquals(AlertStatus.IN_PROGRESS, inProgress.getStatus());
        assertEquals(AlertStatus.CLOSED, closed.getStatus());
        assertEquals(3, repository.findAlertDispositions("alert-1").size());
        assertEquals(DispositionType.ACKNOWLEDGED,
            repository.findAlertDispositions("alert-1").get(0).getDispositionType());
        assertEquals(DispositionType.IN_PROGRESS,
            repository.findAlertDispositions("alert-1").get(1).getDispositionType());
        assertEquals(DispositionType.CLOSED,
            repository.findAlertDispositions("alert-1").get(2).getDispositionType());
        assertEquals(event.getEventId(), repository.getEvents().get(0).getEventId());
        assertEquals(event.getOccurredAt(), repository.getEvents().get(0).getOccurredAt());
    }

    @Test
    void rejectsIncompleteOrInvalidLifecycleTransitions() {
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        repository.saveAlert(new SecurityAlert("alert-1", "AUTH-01", RiskLevel.HIGH, "AUTH-01:alice", "alice",
            AlertStatus.NEW, NOW, NOW, 1));
        AlertLifecycleService lifecycle = new AlertLifecycleService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalArgumentException.class,
            () -> lifecycle.acknowledge("alert-1", "", "Investigating"));
        assertThrows(IllegalArgumentException.class,
            () -> lifecycle.close("alert-1", "operator-1", "", "ticket-42"));
        assertThrows(IllegalStateException.class,
            () -> lifecycle.close("alert-1", "operator-1", "Confirmed mitigation", "ticket-42"));

        SecurityAlert falsePositive = lifecycle.falsePositive("alert-1", "operator-1", "Known test traffic", "case-7");
        assertEquals(AlertStatus.FALSE_POSITIVE, falsePositive.getStatus());
        assertThrows(IllegalStateException.class,
            () -> lifecycle.acknowledge("alert-1", "operator-1", "Retry"));
    }
}
