package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.event.ActionAttempt;
import io.github.jasper.monitoring.api.event.ActionOutcome;

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

    /** Opens a managed fact scope with one idempotent synchronous checkpoint. */
    public static MonitoringFactScope open(MonitoringCheckpoint checkpoint) {
        return new MonitoringFactScope(MonitoringFacts.openScope(checkpoint));
    }

    public static MonitoringFactScope open(Class<? extends ActionType> actionType,
            MonitoringCheckpoint checkpoint) {
        return new MonitoringFactScope(MonitoringFacts.openScope(actionType, checkpoint));
    }

    /** Returns an immutable snapshot without closing this scope. */
    public ActionFacts snapshot() {
        ensureOpen();
        return state.snapshot();
    }

    public ActionAttempt attempt() { ensureOpen(); return state.attempt(); }

    public void complete(ActionOutcome outcome) { ensureOpen(); state.complete(outcome); }

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
