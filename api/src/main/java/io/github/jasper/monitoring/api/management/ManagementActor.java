package io.github.jasper.monitoring.api.management;

import java.util.Objects;

/** Trusted server-side management principal. Never construct from client fields. */
public final class ManagementActor {
    private final String actorId;
    private final String systemScope;
    private ManagementActor(String actorId, String systemScope) { this.actorId = require(actorId, "actorId"); this.systemScope = require(systemScope, "systemScope"); }
    public static ManagementActor of(String actorId, String systemScope) { return new ManagementActor(actorId, systemScope); }
    public String getActorId() { return actorId; }
    public String getSystemScope() { return systemScope; }
    private static String require(String value, String name) { Objects.requireNonNull(value, name); if (value.trim().isEmpty()) throw new IllegalArgumentException(name + " must not be blank"); return value; }
}
