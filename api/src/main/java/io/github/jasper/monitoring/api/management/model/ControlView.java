package io.github.jasper.monitoring.api.management.model;

public class ControlView {
    private final String id;
    private final String systemScope;
    private final String status;
    private final long version;

    protected ControlView(String id, String scope, String status, long version) {
        this.id = ManagementModelValidation.text(id, "id");
        this.systemScope = ManagementModelValidation.text(scope, "systemScope");
        this.status = ManagementModelValidation.controlStatus(status);
        this.version = ManagementModelValidation.positive(version, "version");
    }

    public static ControlView of(String id, String scope, long version) {
        return new ControlView(id, scope, "UNKNOWN", version);
    }

    public static ControlView of(String id, String scope, String status, long version) {
        return new ControlView(id, scope, status, version);
    }

    public String getId() { return id; }
    public String getSystemScope() { return systemScope; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }
}
