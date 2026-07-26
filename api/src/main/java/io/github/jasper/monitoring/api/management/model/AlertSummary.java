package io.github.jasper.monitoring.api.management.model;

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

    public String getId() { return id; }
    public String getSystemScope() { return systemScope; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }
}
