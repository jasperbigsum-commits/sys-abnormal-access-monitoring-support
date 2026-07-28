package io.github.jasper.monitoring.api.management.model;

import io.github.jasper.monitoring.api.rule.RuleMode;

/** 暴露给管理适配器的当前规则持久化版本视图。 */
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

    /** @return 规则视图对象 */
    public static RuleView of(String id, String scope, long version, RuleMode mode, long threshold) {
        return new RuleView(id, scope, version, mode, threshold);
    }

    /** @return 规则标识 */
    public String getId() { return id; }
    /** @return 系统作用域 */
    public String getSystemScope() { return systemScope; }
    /** @return 版本号 */
    public long getVersion() { return version; }
    /** @return 规则模式 */
    public RuleMode getMode() { return mode; }
    /** @return 阈值 */
    public long getThreshold() { return threshold; }
}
