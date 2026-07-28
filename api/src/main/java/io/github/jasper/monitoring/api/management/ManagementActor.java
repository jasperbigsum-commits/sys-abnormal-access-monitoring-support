package io.github.jasper.monitoring.api.management;

import java.util.Objects;

/** 服务端可信的管理操作者身份，不应直接由客户端字段构造。 */
public final class ManagementActor {
    private final String actorId;
    private final String systemScope;

    private ManagementActor(String actorId, String systemScope) {
        this.actorId = require(actorId, "actorId");
        this.systemScope = require(systemScope, "systemScope");
    }

    /** @return 可信管理操作者 */
    public static ManagementActor of(String actorId, String systemScope) {
        return new ManagementActor(actorId, systemScope);
    }

    /** @return 操作者标识 */
    public String getActorId() { return actorId; }
    /** @return 操作者所属系统作用域 */
    public String getSystemScope() { return systemScope; }

    private static String require(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
