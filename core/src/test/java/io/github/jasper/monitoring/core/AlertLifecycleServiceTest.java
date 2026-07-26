package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringStateException;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import io.github.jasper.monitoring.core.application.AlertLifecycleService;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.infrastructure.memory.InMemoryAlertStore;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import io.github.jasper.monitoring.api.AlertStatus;
import io.github.jasper.monitoring.api.DispositionType;
import io.github.jasper.monitoring.api.RiskLevel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AlertLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T01:10:00Z");

    @Test
    void rollsBackInMemoryChangesWhenTransactionWorkFails() {
        InMemoryAlertStore repository = new InMemoryAlertStore();

        assertThrows(IllegalStateException.class, () -> repository.required(() -> {
            repository.save(new SecurityAlert("alert-rollback", "AUTH-01", RiskLevel.HIGH,
                "AUTH-01:alice", "alice", AlertStatus.NEW, NOW, NOW, 1));
            throw new IllegalStateException("simulated failure");
        }));

        assertEquals(0, repository.getAlerts().size());
    }

    @Test
    void acknowledgesAndClosesWithAppendOnlyHistory() {
        InMemoryAlertStore repository = new InMemoryAlertStore();
        repository.save(new SecurityAlert("alert-1", "AUTH-01", RiskLevel.HIGH, "AUTH-01:alice", "alice",
            AlertStatus.NEW, NOW.minusSeconds(60), NOW.minusSeconds(60), 1));

        AlertLifecycleService lifecycle = new AlertLifecycleService(repository, repository, Clock.fixed(NOW, ZoneOffset.UTC));

        SecurityAlert acknowledged = lifecycle.acknowledge("alert-1", "operator-1", "Investigating login failures");
        SecurityAlert inProgress = lifecycle.startInvestigation("alert-1", "operator-1", "Assigned to incident queue");
        SecurityAlert closed = lifecycle.close("alert-1", "operator-1", "Confirmed mitigation", "ticket-42");

        assertEquals(AlertStatus.ACKNOWLEDGED, acknowledged.getStatus());
        assertEquals(AlertStatus.IN_PROGRESS, inProgress.getStatus());
        assertEquals(AlertStatus.CLOSED, closed.getStatus());
        assertEquals(3, repository.findDispositions("alert-1").size());
        assertEquals(DispositionType.ACKNOWLEDGED,
            repository.findDispositions("alert-1").get(0).getDispositionType());
        assertEquals(DispositionType.IN_PROGRESS,
            repository.findDispositions("alert-1").get(1).getDispositionType());
        assertEquals(DispositionType.CLOSED,
            repository.findDispositions("alert-1").get(2).getDispositionType());
    }

    @Test
    void rejectsIncompleteOrInvalidLifecycleTransitions() {
        InMemoryAlertStore repository = new InMemoryAlertStore();
        repository.save(new SecurityAlert("alert-1", "AUTH-01", RiskLevel.HIGH, "AUTH-01:alice", "alice",
            AlertStatus.NEW, NOW, NOW, 1));
        AlertLifecycleService lifecycle = new AlertLifecycleService(repository, repository, Clock.fixed(NOW, ZoneOffset.UTC));

        MonitoringValidationException missingOperator = assertThrows(MonitoringValidationException.class,
            () -> lifecycle.acknowledge("alert-1", "", "Investigating"));
        assertEquals(MonitoringErrorCode.REQUIRED_FIELD_MISSING, missingOperator.getErrorCode());
        MonitoringValidationException missingComment = assertThrows(MonitoringValidationException.class,
            () -> lifecycle.close("alert-1", "operator-1", "", "ticket-42"));
        assertEquals(MonitoringErrorCode.REQUIRED_FIELD_MISSING, missingComment.getErrorCode());
        MonitoringStateException invalidClose = assertThrows(MonitoringStateException.class,
            () -> lifecycle.close("alert-1", "operator-1", "Confirmed mitigation", "ticket-42"));
        assertEquals(MonitoringErrorCode.INVALID_ALERT_TRANSITION, invalidClose.getErrorCode());

        SecurityAlert falsePositive = lifecycle.falsePositive("alert-1", "operator-1", "Known test traffic", "case-7");
        assertEquals(AlertStatus.FALSE_POSITIVE, falsePositive.getStatus());
        MonitoringStateException terminalTransition = assertThrows(MonitoringStateException.class,
            () -> lifecycle.acknowledge("alert-1", "operator-1", "Retry"));
        assertEquals(MonitoringErrorCode.INVALID_ALERT_TRANSITION, terminalTransition.getErrorCode());

        MonitoringValidationException missingAlert = assertThrows(MonitoringValidationException.class,
            () -> lifecycle.acknowledge("alert-missing", "operator-1", "Investigating"));
        assertEquals(MonitoringErrorCode.ALERT_NOT_FOUND, missingAlert.getErrorCode());
    }
}
