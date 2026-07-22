package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.AlertStatus;
import io.github.jasper.monitoring.api.DispositionType;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Applies validated alert lifecycle decisions while retaining an immutable audit trail.
 * Status changes and their corresponding dispositions are persisted together by the repository implementation.
 */
public final class AlertLifecycleService {
    private final MonitoringRepository repository;
    private final Clock clock;

    /**
     * @param repository persistence port for alerts and append-only dispositions
     * @param clock clock used to timestamp the server-side decision
     */
    public AlertLifecycleService(MonitoringRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Acknowledges a new alert.
     *
     * @param alertId alert to acknowledge
     * @param operatorId authenticated operator identifier
     * @param commentText non-blank acknowledgement rationale
     * @return the alert with {@link AlertStatus#ACKNOWLEDGED} status
     * @throws IllegalStateException if the alert is not new
     */
    public SecurityAlert acknowledge(String alertId, String operatorId, String commentText) {
        return transition(alertId, operatorId, commentText, null, DispositionType.ACKNOWLEDGED, AlertStatus.ACKNOWLEDGED);
    }

    /**
     * Marks an acknowledged alert as under investigation.
     *
     * @return the alert with {@link AlertStatus#IN_PROGRESS} status
     * @throws IllegalStateException if the alert has not been acknowledged
     */
    public SecurityAlert startInvestigation(String alertId, String operatorId, String commentText) {
        return transition(alertId, operatorId, commentText, null, DispositionType.IN_PROGRESS, AlertStatus.IN_PROGRESS);
    }

    /**
     * Closes an acknowledged or in-progress alert and records supporting evidence.
     *
     * @param alertId alert to close
     * @param operatorId authenticated operator identifier
     * @param commentText non-blank closure rationale
     * @param evidenceSummary non-blank evidence supporting closure
     * @return the alert with {@link AlertStatus#CLOSED} status
     * @throws IllegalStateException if the alert is not acknowledged or under investigation
     */
    public SecurityAlert close(String alertId, String operatorId, String commentText, String evidenceSummary) {
        requireText(evidenceSummary, "evidenceSummary");
        return transition(alertId, operatorId, commentText, evidenceSummary, DispositionType.CLOSED, AlertStatus.CLOSED);
    }

    /**
     * Resolves an open alert as a false positive while retaining its operator decision.
     *
     * @return the alert with {@link AlertStatus#FALSE_POSITIVE} status
     * @throws IllegalStateException if the alert is already terminal
     */
    public SecurityAlert falsePositive(String alertId, String operatorId, String commentText, String evidenceSummary) {
        return transition(alertId, operatorId, commentText, evidenceSummary, DispositionType.FALSE_POSITIVE,
            AlertStatus.FALSE_POSITIVE);
    }

    private SecurityAlert transition(String alertId, String operatorId, String commentText, String evidenceSummary,
                                     DispositionType dispositionType, AlertStatus targetStatus) {
        requireText(alertId, "alertId");
        requireText(operatorId, "operatorId");
        requireText(commentText, "commentText");
        SecurityAlert alert = repository.findAlert(alertId)
            .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));
        assertTransitionAllowed(alert.getStatus(), dispositionType);

        Instant now = Instant.now(clock);
        AlertDisposition disposition = new AlertDisposition(UUID.randomUUID().toString(), alertId, dispositionType,
            operatorId, commentText, evidenceSummary, now);
        SecurityAlert updated = alert.withStatus(targetStatus);
        repository.appendAlertDisposition(disposition);
        repository.saveAlert(updated);
        return updated;
    }

    private static void assertTransitionAllowed(AlertStatus currentStatus, DispositionType dispositionType) {
        if (dispositionType == DispositionType.ACKNOWLEDGED) {
            if (currentStatus == AlertStatus.NEW) { return; }
            throw new IllegalStateException("Only new alerts can be acknowledged");
        }
        if (dispositionType == DispositionType.CLOSED) {
            if (currentStatus == AlertStatus.ACKNOWLEDGED || currentStatus == AlertStatus.IN_PROGRESS) { return; }
            throw new IllegalStateException("Only acknowledged or in-progress alerts can be closed");
        }
        if (dispositionType == DispositionType.IN_PROGRESS) {
            if (currentStatus == AlertStatus.ACKNOWLEDGED) { return; }
            throw new IllegalStateException("Only acknowledged alerts can enter investigation");
        }
        if (dispositionType == DispositionType.FALSE_POSITIVE && currentStatus.isOpen()) { return; }
        throw new IllegalStateException("Terminal alerts cannot be changed");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
