package io.github.jasper.monitoring.api.management;

import java.util.Objects;

/** 管理操作授权时使用的资源标识。 */
public final class ManagementResource {
    private final String type;
    private final String id;
    private final String systemScope;

    private ManagementResource(String type, String id, String scope) {
        this.type = require(type);
        this.id = require(id);
        this.systemScope = require(scope);
    }

    /** @return 授权资源对象 */
    public static ManagementResource of(String type, String id, String systemScope) {
        return new ManagementResource(type, id, systemScope);
    }

    /** @return 资源类型 */
    public String getType() { return type; }
    /** @return 资源标识 */
    public String getId() { return id; }
    /** @return 资源所属系统作用域 */
    public String getSystemScope() { return systemScope; }

    private static String require(String s) {
        Objects.requireNonNull(s);
        if (s.trim().isEmpty()) throw new IllegalArgumentException("value must not be blank");
        return s;
    }
}
