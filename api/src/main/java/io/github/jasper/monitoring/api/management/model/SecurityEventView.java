package io.github.jasper.monitoring.api.management.model;

import java.util.Objects;

/** Minimal event identity exposed at the management boundary. */
public final class SecurityEventView {
    private final String id;
    private final String systemScope;

    private SecurityEventView(String id, String scope) {
        this.id = bounded(id, "id");
        this.systemScope = bounded(scope, "systemScope");
    }

    public static SecurityEventView of(String id, String scope) {
        return new SecurityEventView(id, scope);
    }

    public String getId() { return id; }
    public String getSystemScope() { return systemScope; }

    private static String bounded(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty() || value.length() > 256) {
            throw new IllegalArgumentException(name + " must be non-empty and bounded");
        }
        return value;
    }
}
