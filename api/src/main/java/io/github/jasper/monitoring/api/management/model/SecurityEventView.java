package io.github.jasper.monitoring.api.management.model;

import java.util.Objects;

/** 管理边界暴露的最小安全事件身份视图。 */
public final class SecurityEventView {
    private final String id;
    private final String systemScope;

    private SecurityEventView(String id, String scope) {
        this.id = bounded(id, "id");
        this.systemScope = bounded(scope, "systemScope");
    }

    /** @return 安全事件视图对象 */
    public static SecurityEventView of(String id, String scope) {
        return new SecurityEventView(id, scope);
    }

    /** @return 事件标识 */
    public String getId() { return id; }
    /** @return 系统作用域 */
    public String getSystemScope() { return systemScope; }

    private static String bounded(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty() || value.length() > 256) {
            throw new IllegalArgumentException(name + " must be non-empty and bounded");
        }
        return value;
    }
}
