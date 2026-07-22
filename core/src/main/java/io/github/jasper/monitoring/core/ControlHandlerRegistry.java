package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.ControlActionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Resolves host control handlers by action type. Handler order is the configured precedence. */
public final class ControlHandlerRegistry {
    private final List<ControlHandler> handlers;
    /** @param handlers ordered host control handlers; the list is copied defensively */
    public ControlHandlerRegistry(List<ControlHandler> handlers) {
        this.handlers = Collections.unmodifiableList(new ArrayList<ControlHandler>(handlers));
    }
    /** @return a registry without handlers, suitable only for observation mode */
    public static ControlHandlerRegistry empty() { return new ControlHandlerRegistry(Collections.<ControlHandler>emptyList()); }
    /** @return the first configured handler that supports {@code action}, if any */
    public Optional<ControlHandler> find(ControlActionType action) {
        for (ControlHandler handler : handlers) {
            if (handler.supports(action)) { return Optional.of(handler); }
        }
        return Optional.empty();
    }
    /** @return whether any configured handler supports {@code action} */
    public boolean supports(ControlActionType action) { return find(action).isPresent(); }
    /** @return whether no host control handler is configured */
    public boolean isEmpty() { return handlers.isEmpty(); }
}
