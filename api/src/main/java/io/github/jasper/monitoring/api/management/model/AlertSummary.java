package io.github.jasper.monitoring.api.management.model;

/** 告警摘要视图。 */
public class AlertSummary {
    private final String id;
    private final String systemScope;
    private final String status;
    private final long version;

    public AlertSummary(String id, String scope, String status, long version) {
        this.id = ManagementModelValidation.text(id, "id");
        this.systemScope = ManagementModelValidation.text(scope, "systemScope");
        this.status = ManagementModelValidation.alertStatus(status);
        this.version = ManagementModelValidation.positive(version, "version");
    }

    /** @return 告警标识 */
    public String getId() { return id; }
    /** @return 系统作用域 */
    public String getSystemScope() { return systemScope; }
    /** @return 告警状态 */
    public String getStatus() { return status; }
    /** @return 版本号 */
    public long getVersion() { return version; }
}
