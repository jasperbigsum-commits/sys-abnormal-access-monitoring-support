package io.github.jasper.monitoring.api.fact;

/** Produces action facts; applicability is owned exclusively by {@link FactBinding}. */
@FunctionalInterface
public interface ActionFactProvider {

    /** @return facts observed by this provider for the current invocation */
    ActionFacts provide();
}
