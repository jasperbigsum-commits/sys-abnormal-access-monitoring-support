package io.github.jasper.monitoring.api;

/**
 * Host extension that contributes approved dynamic facts for one annotated action invocation.
 *
 * <p>Implementations must not infer or replace trusted request, identity, authorization, action,
 * or event-time fields.</p>
 */
public interface MonitorActionEnricher {
    /**
     * Collects dynamic, non-sensitive facts for the supplied invocation phase.
     *
     * @param invocation immutable action invocation snapshot
     * @return collected facts, or {@link MonitorActionFacts#empty()} when nothing is available
     */
    MonitorActionFacts enrich(MonitorActionInvocation invocation);
}
