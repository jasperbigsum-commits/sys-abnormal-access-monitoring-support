package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.DispositionType;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable, append-only operator decision attached to an alert.
 * The disposition record is retained even after the alert reaches a terminal status.
 */
public final class AlertDisposition {
    private final String dispositionId;
    private final String alertId;
    private final DispositionType dispositionType;
    private final String operatorId;
    private final String commentText;
    private final String evidenceSummary;
    private final Instant createdAt;

    /**
     * Creates an auditable lifecycle decision.
     *
     * @param dispositionId unique identifier for this immutable record
     * @param alertId identifier of the alert being updated
     * @param dispositionType lifecycle action taken by the operator
     * @param operatorId authenticated operator who made the decision
     * @param commentText required operator rationale, validated by the lifecycle service
     * @param evidenceSummary supporting evidence for terminal decisions, when applicable
     * @param createdAt server-side decision timestamp
     */
    public AlertDisposition(String dispositionId, String alertId, DispositionType dispositionType, String operatorId,
                            String commentText, String evidenceSummary, Instant createdAt) {
        this.dispositionId = requiredText(dispositionId, "dispositionId");
        this.alertId = requiredText(alertId, "alertId");
        this.dispositionType = Objects.requireNonNull(dispositionType, "dispositionType");
        this.operatorId = requiredText(operatorId, "operatorId");
        this.commentText = commentText;
        this.evidenceSummary = evidenceSummary;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public String getDispositionId() { return dispositionId; }
    public String getAlertId() { return alertId; }
    public DispositionType getDispositionType() { return dispositionType; }
    public String getOperatorId() { return operatorId; }
    public String getCommentText() { return commentText; }
    public String getEvidenceSummary() { return evidenceSummary; }
    public Instant getCreatedAt() { return createdAt; }

    private static String requiredText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
