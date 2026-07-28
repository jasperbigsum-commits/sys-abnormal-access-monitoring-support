package io.github.jasper.monitoring.api.management.command;

import java.util.Objects;

/** 告警分配命令，使用乐观锁控制并发更新。 */
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

    /** @return 告警分配命令对象 */
    public static AlertAssignmentCommand of(String alertId, long expectedVersion, String assigneeId,
                                            String reason, String idempotencyKey) {
        return new AlertAssignmentCommand(alertId, expectedVersion, assigneeId, reason, idempotencyKey);
    }

    /** @return 告警标识 */
    public String getAlertId() { return getResourceId(); }
    /** @return 被分配处理人的标识 */
    public String getAssigneeId() { return assigneeId; }
}
