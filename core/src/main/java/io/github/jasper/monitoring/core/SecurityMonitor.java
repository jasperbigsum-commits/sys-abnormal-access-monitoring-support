package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.SecurityEventDraft;

/** Primary application-facing entry point for explicit domain event collection. */
public interface SecurityMonitor {
    /**
     * Persists and evaluates a validated server-side event draft.
     * In observation mode this never invokes controls; in enforce mode it may invoke configured host handlers.
     *
     * @param draft validated event data with no raw secrets or client-authoritative identity fields
     * @return the persisted event and all monitoring effects derived from it
     */
    MonitoringOutcome record(SecurityEventDraft draft);
}
