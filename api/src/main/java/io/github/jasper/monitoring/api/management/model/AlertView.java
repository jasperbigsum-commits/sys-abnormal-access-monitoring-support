package io.github.jasper.monitoring.api.management.model;

/** Current alert lifecycle projection returned to host management adapters. */
public final class AlertView {
    private final String id;
    private final String systemScope;
    private final String status;
    private final String assigneeId;
    private final long version;

    private AlertView(String id, String scope, String status, String assigneeId, long version) {
        if (id == null || id.trim().isEmpty() || scope == null || scope.trim().isEmpty()
            || status == null || status.trim().isEmpty() || version < 0) {
            throw new IllegalArgumentException("invalid alert view");
        }
        this.id = id;
        this.systemScope = scope;
        this.status = status;
        this.assigneeId = assigneeId;
        this.version = version;
    }

    public static AlertView of(String id, String scope, String status, String assigneeId, long version) {
        if (assigneeId != null && (assigneeId.trim().isEmpty() || assigneeId.length() > 128)) {
            throw new IllegalArgumentException("invalid alert assignee");
        }
        return new AlertView(id, scope, status, assigneeId, version);
    }

    public String getId() { return id; }
    public String getSystemScope() { return systemScope; }
    public String getStatus() { return status; }
    public String getAssigneeId() { return assigneeId; }
    public long getVersion() { return version; }
}
