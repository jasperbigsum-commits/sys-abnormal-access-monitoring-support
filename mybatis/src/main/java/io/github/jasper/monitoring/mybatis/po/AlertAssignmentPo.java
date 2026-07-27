package io.github.jasper.monitoring.mybatis.po;

import java.time.Instant;

/** Internal MyBatis row for alert assignment history. */
public final class AlertAssignmentPo {
    private String id;
    private String alertId;
    private String operatorId;
    private String assigneeId;
    private String reason;
    private long expectedVersion;
    private Instant createdAt;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAlertId() { return alertId; }
    public void setAlertId(String alertId) { this.alertId = alertId; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public long getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(long expectedVersion) { this.expectedVersion = expectedVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
