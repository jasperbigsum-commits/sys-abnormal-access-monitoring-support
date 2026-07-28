package io.github.jasper.monitoring.api.management.model;

/** 返回给宿主管理适配器的白名单版本化状态视图。 */
public final class WhitelistView {
    private final String id;
    private final String systemScope;
    private final String status;
    private final long version;

    private WhitelistView(String id, String systemScope, String status, long version) {
        if (id == null || id.trim().isEmpty() || systemScope == null || systemScope.trim().isEmpty()
                || status == null || status.trim().isEmpty() || version < 1L) {
            throw new IllegalArgumentException("invalid whitelist view");
        }
        this.id = id;
        this.systemScope = systemScope;
        this.status = status;
        this.version = version;
    }

    /** @return 白名单视图对象 */
    public static WhitelistView of(String id, String systemScope, String status, long version) {
        return new WhitelistView(id, systemScope, status, version);
    }

    /** @return 兼容旧仓储的默认激活状态白名单视图 */
    public static WhitelistView of(String id, String systemScope) {
        return new WhitelistView(id, systemScope, "ACTIVE", 1L);
    }

    /** @return 白名单标识 */
    public String getId() {
        return id;
    }

    /** @return 系统作用域 */
    public String getSystemScope() {
        return systemScope;
    }

    /** @return 白名单状态 */
    public String getStatus() {
        return status;
    }

    /** @return 版本号 */
    public long getVersion() {
        return version;
    }
}
