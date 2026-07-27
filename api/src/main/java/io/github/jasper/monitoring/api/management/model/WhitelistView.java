package io.github.jasper.monitoring.api.management.model;

/** Current versioned whitelist state returned to host management adapters. */
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

    public static WhitelistView of(String id, String systemScope, String status, long version) {
        return new WhitelistView(id, systemScope, status, version);
    }

    /** Compatibility factory for repositories that expose only an active first version. */
    public static WhitelistView of(String id, String systemScope) {
        return new WhitelistView(id, systemScope, "ACTIVE", 1L);
    }

    public String getId() {
        return id;
    }

    public String getSystemScope() {
        return systemScope;
    }

    public String getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }
}
