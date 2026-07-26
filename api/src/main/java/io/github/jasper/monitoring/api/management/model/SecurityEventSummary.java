package io.github.jasper.monitoring.api.management.model;

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

    public String getId() { return id; }
    public String getSystemScope() { return systemScope; }
    public String getActionCode() { return actionCode; }
    public String getStatus() { return status; }
    public long getOccurredAt() { return occurredAt; }
}
