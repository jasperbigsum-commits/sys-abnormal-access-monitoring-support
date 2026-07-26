package io.github.jasper.monitoring.api.fact;

import io.github.jasper.monitoring.api.event.ActionExecution;

/** Produces action facts; applicability is owned exclusively by {@link FactBinding}. */
@FunctionalInterface
public interface ActionFactProvider {

    /**
     * Produces facts for the supplied read-only execution context.
     *
     * @param execution current action execution
     * @return facts observed by this provider for the current invocation
     */
    ActionFacts provide(ActionExecution execution);
}
