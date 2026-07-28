package io.github.jasper.monitoring.api.management.model;

/** 返回给宿主管理适配器的告警生命周期当前投影。 */
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

    /** @return 告警视图对象 */
    public static AlertView of(String id, String scope, String status, String assigneeId, long version) {
        if (assigneeId != null && (assigneeId.trim().isEmpty() || assigneeId.length() > 128)) {
            throw new IllegalArgumentException("invalid alert assignee");
        }
        return new AlertView(id, scope, status, assigneeId, version);
    }

    /** @return 告警标识 */
    public String getId() { return id; }
    /** @return 系统作用域 */
    public String getSystemScope() { return systemScope; }
    /** @return 生命周期状态 */
    public String getStatus() { return status; }
    /** @return 当前处理人标识；未分配时为 {@code null} */
    public String getAssigneeId() { return assigneeId; }
    /** @return 当前版本号 */
    public long getVersion() { return version; }
}
