package io.github.jasper.monitoring.api.management;
import java.util.Objects;
public final class ManagementResource {
    private final String type; private final String id; private final String systemScope;
    private ManagementResource(String type, String id, String scope) { this.type = require(type); this.id = require(id); this.systemScope = require(scope); }
    public static ManagementResource of(String type, String id, String systemScope) { return new ManagementResource(type,id,systemScope); }
    public String getType(){return type;} public String getId(){return id;} public String getSystemScope(){return systemScope;}
    private static String require(String s){Objects.requireNonNull(s);if(s.trim().isEmpty())throw new IllegalArgumentException("value must not be blank");return s;}
}
