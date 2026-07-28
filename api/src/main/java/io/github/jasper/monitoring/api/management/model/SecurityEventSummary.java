package io.github.jasper.monitoring.api.management.model;

/** 安全事件摘要视图。 */
public class SecurityEventSummary {
    private final String id;
    private final String systemScope;
    private final String actionCode;
    private final String status;
    private final long occurredAt;

    public SecurityEventSummary(String id, String scope, String action, String status, long occurredAt) {
        this.id = ManagementModelValidation.text(id, "id");
        this.systemScope = ManagementModelValidation.text(scope, "systemScope");
        this.actionCode = ManagementModelValidation.code(action, "actionCode");
        this.status = ManagementModelValidation.status(status, "status");
        this.occurredAt = ManagementModelValidation.timestamp(occurredAt, "occurredAt");
    }

    /** @return 事件标识 */
    public String getId() { return id; }
    /** @return 系统作用域 */
    public String getSystemScope() { return systemScope; }
    /** @return 动作编码 */
    public String getActionCode() { return actionCode; }
    /** @return 事件状态 */
    public String getStatus() { return status; }
    /** @return 事件发生时间戳（毫秒） */
    public long getOccurredAt() { return occurredAt; }
}
