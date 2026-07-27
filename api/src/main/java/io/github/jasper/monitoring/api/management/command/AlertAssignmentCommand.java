package io.github.jasper.monitoring.api.management.command;

import java.util.Objects;

/** Assigns an alert to a host-system operator using optimistic locking. */
public final class AlertAssignmentCommand extends VersionedReasonCommand {
    private final String assigneeId;

    private AlertAssignmentCommand(String alertId, long expectedVersion, String assigneeId,
                                   String reason, String idempotencyKey) {
        super(alertId, expectedVersion, reason, idempotencyKey);
        Objects.requireNonNull(assigneeId, "assigneeId");
        if (assigneeId.trim().isEmpty() || assigneeId.length() > 128) {
            throw new IllegalArgumentException("assigneeId must be non-empty and bounded");
        }
        this.assigneeId = assigneeId;
    }

    public static AlertAssignmentCommand of(String alertId, long expectedVersion, String assigneeId,
                                            String reason, String idempotencyKey) {
        return new AlertAssignmentCommand(alertId, expectedVersion, assigneeId, reason, idempotencyKey);
    }

    public String getAlertId() { return getResourceId(); }
    public String getAssigneeId() { return assigneeId; }
}
