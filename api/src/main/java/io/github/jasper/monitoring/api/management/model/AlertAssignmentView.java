package io.github.jasper.monitoring.api.management.model;

import java.time.Instant;
import java.util.Objects;

/** Immutable alert assignment history entry exposed to management adapters. */
public final class AlertAssignmentView {
    private final String id;
    private final String alertId;
    private final String operatorId;
    private final String assigneeId;
    private final String reason;
    private final long expectedVersion;
    private final Instant createdAt;

    private AlertAssignmentView(String id, String alertId, String operatorId, String assigneeId, String reason,
                                long expectedVersion, Instant createdAt) {
        this.id = required(id, "id");
        this.alertId = required(alertId, "alertId");
        this.operatorId = required(operatorId, "operatorId");
        this.assigneeId = required(assigneeId, "assigneeId");
        this.reason = required(reason, "reason");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must be non-negative");
        this.expectedVersion = expectedVersion;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static AlertAssignmentView of(String id, String alertId, String operatorId, String assigneeId,
                                         String reason, long expectedVersion, Instant createdAt) {
        return new AlertAssignmentView(id, alertId, operatorId, assigneeId, reason, expectedVersion, createdAt);
    }

    public String getId() { return id; }
    public String getAlertId() { return alertId; }
    public String getOperatorId() { return operatorId; }
    public String getAssigneeId() { return assigneeId; }
    public String getReason() { return reason; }
    public long getExpectedVersion() { return expectedVersion; }
    public Instant getCreatedAt() { return createdAt; }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
