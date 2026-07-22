package io.github.jasper.monitoring.api;

/**
 * Optional host extension for adding approved, non-sensitive event fields.
 *
 * <p>Implementations must preserve the supplied event's security meaning and must not add
 * credentials, cookies, tokens, or raw sensitive payloads.</p>
 */
public interface EventEnricher {
    /**
     * Adds host-approved context to an event before monitoring.
     *
     * @param draft the sanitized draft created by the integration adapter
     * @param request trusted request facts
     * @param identity identity resolved by the host backend
     * @return the draft to persist and evaluate; never {@code null}
     */
    SecurityEventDraft enrich(SecurityEventDraft draft, MonitoringRequestContext request, IdentityContext identity);
}
