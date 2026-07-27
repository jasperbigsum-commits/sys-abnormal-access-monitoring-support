package io.github.jasper.monitoring.api.management.model;

import io.github.jasper.monitoring.api.rule.RuleMode;

/** Current persisted rule version exposed to management adapters. */
public final class RuleView {
    private final String id;
    private final String systemScope;
    private final long version;
    private final RuleMode mode;
    private final long threshold;

    private RuleView(String id, String scope, long version, RuleMode mode, long threshold) {
        if (id == null || id.trim().isEmpty() || scope == null || scope.trim().isEmpty()
            || version < 1 || threshold < 1) {
            throw new IllegalArgumentException("invalid rule view");
        }
        this.id = id;
        this.systemScope = scope;
        this.version = version;
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
        this.threshold = threshold;
    }

    public static RuleView of(String id, String scope, long version, RuleMode mode, long threshold) {
        return new RuleView(id, scope, version, mode, threshold);
    }

    public String getId() { return id; }
    public String getSystemScope() { return systemScope; }
    public long getVersion() { return version; }
    public RuleMode getMode() { return mode; }
    public long getThreshold() { return threshold; }
}
