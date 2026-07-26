package io.github.jasper.monitoring.api.control;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable startup catalogue of control types and their host handler bindings. */
public final class ControlCatalog<H> {
    private final Map<ControlType, H> handlers;
    private final boolean enforce;

    private ControlCatalog(Map<ControlType, H> handlers, boolean enforce) {
        this.handlers = Collections.unmodifiableMap(new EnumMap<ControlType, H>(handlers));
        this.enforce = enforce;
    }

    public H require(ControlType type) {
        H handler = handlers.get(Objects.requireNonNull(type, "type"));
        if (handler == null) throw new IllegalStateException("No handler registered for " + type);
        return handler;
    }
    public boolean contains(ControlType type) { return handlers.containsKey(type); }
    public Map<ControlType, H> handlers() { return handlers; }

    public static <H> Builder<H> builder() { return new Builder<H>(); }

    public static final class Builder<H> {
        private final Map<ControlType, H> handlers = new EnumMap<ControlType, H>(ControlType.class);
        private boolean enforce;
        public Builder<H> enforce(boolean value) { enforce = value; return this; }
        public Builder<H> bind(ControlType type, H handler) {
            Objects.requireNonNull(type, "type"); Objects.requireNonNull(handler, "handler");
            if (handlers.containsKey(type)) throw new IllegalArgumentException("Duplicate control handler: " + type);
            handlers.put(type, handler); return this;
        }
        public ControlCatalog<H> freeze() {
            if (enforce) {
                for (ControlType type : ControlType.values()) {
                    if (!type.requiresApproval() && !handlers.containsKey(type))
                        throw new IllegalStateException("ENFORCE requires handler for " + type);
                }
            }
            return new ControlCatalog<H>(handlers, enforce);
        }
    }
}
