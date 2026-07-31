package io.github.jasper.monitoring.api.error;

import io.github.jasper.monitoring.api.action.ActionDecision;
import java.util.Objects;

/** Indicates that a completed-facts checkpoint blocked the current action attempt. */
public final class ActionBlockedException extends RuntimeException implements MonitoringFailure {
    private final ActionDecision decision;

    public ActionBlockedException(ActionDecision decision) {
        super("Action blocked by monitoring rule");
        this.decision = Objects.requireNonNull(decision, "decision");
        if (decision.isAllowed()) throw new IllegalArgumentException("decision must block the action");
    }

    public ActionDecision getDecision() { return decision; }

    @Override public MonitoringErrorCode getErrorCode() {
        return MonitoringErrorCode.ACTION_BLOCKED;
    }
}
