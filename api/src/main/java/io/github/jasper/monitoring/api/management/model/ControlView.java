package io.github.jasper.monitoring.api.management.model;

/** 控制记录摘要视图。 */
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

    /** @return 未知状态的控制摘要视图 */
    public static ControlView of(String id, String scope, long version) {
        return new ControlView(id, scope, "UNKNOWN", version);
    }

    /** @return 指定状态的控制摘要视图 */
    public static ControlView of(String id, String scope, String status, long version) {
        return new ControlView(id, scope, status, version);
    }

    /** @return 控制标识 */
    public String getId() { return id; }
    /** @return 系统作用域 */
    public String getSystemScope() { return systemScope; }
    /** @return 控制状态 */
    public String getStatus() { return status; }
    /** @return 版本号 */
    public long getVersion() { return version; }
}
