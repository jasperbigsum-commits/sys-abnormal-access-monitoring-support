package io.github.jasper.monitoring.api.management.model;

import java.time.Instant;
import java.util.Objects;

/** 暴露给管理适配器的不可变告警分配历史条目。 */
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

    /** @return 告警分配历史视图对象 */
    public static AlertAssignmentView of(String id, String alertId, String operatorId, String assigneeId,
                                         String reason, long expectedVersion, Instant createdAt) {
        return new AlertAssignmentView(id, alertId, operatorId, assigneeId, reason, expectedVersion, createdAt);
    }

    /** @return 历史记录标识 */
    public String getId() { return id; }
    /** @return 告警标识 */
    public String getAlertId() { return alertId; }
    /** @return 操作人标识 */
    public String getOperatorId() { return operatorId; }
    /** @return 被分配处理人标识 */
    public String getAssigneeId() { return assigneeId; }
    /** @return 分配原因 */
    public String getReason() { return reason; }
    /** @return 对应的期望版本 */
    public long getExpectedVersion() { return expectedVersion; }
    /** @return 记录创建时间 */
    public Instant getCreatedAt() { return createdAt; }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
