package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.error.ActionBlockedException;

/** Explicit synchronization point between completed facts and an irreversible side effect. */
public final class MonitoringGate {
    private MonitoringGate() { }

    public static ActionDecision checkpoint() {
        ActionDecision decision = MonitoringFacts.checkpoint();
        if (!decision.isAllowed()) throw new ActionBlockedException(decision);
        return decision;
    }
}
