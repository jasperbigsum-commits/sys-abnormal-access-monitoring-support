package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.fact.ActionFacts;

/** Lifecycle handle created by annotation instrumentation for one synchronous invocation. */
public final class MonitoringFactScope implements AutoCloseable {
    private final MonitoringFacts.ScopeState state;
    private boolean closed;

    private MonitoringFactScope(MonitoringFacts.ScopeState state) {
        this.state = state;
    }

    /** Opens a new innermost fact scope for the current thread. */
    public static MonitoringFactScope open() {
        return new MonitoringFactScope(MonitoringFacts.openScope());
    }

    /** Returns an immutable snapshot without closing this scope. */
    public ActionFacts snapshot() {
        ensureOpen();
        return state.snapshot();
    }

    /** Closes this scope and removes thread state when it was the outermost scope. */
    @Override
    public void close() {
        ensureOpen();
        MonitoringFacts.closeScope(state);
        closed = true;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Monitoring fact scope is already closed");
    }
}
