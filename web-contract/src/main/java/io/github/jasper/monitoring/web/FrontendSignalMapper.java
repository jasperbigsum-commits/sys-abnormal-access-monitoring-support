package io.github.jasper.monitoring.web;

import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;

/**
 * Converts validated browser telemetry plus trusted server context into a monitoring event.
 *
 * <p>Only {@code SENSITIVE_VIEW} maps to {@link SecurityEventType#VIEW_SENSITIVE}; other actions
 * map to {@link SecurityEventType#QUERY}. The browser cannot choose the event outcome, user,
 * account type, roles, session, or source IP.</p>
 */
public final class FrontendSignalMapper {
    private FrontendSignalMapper() { }

    /**
     * Creates an event draft from supplemental client evidence and authoritative server facts.
     *
     * @param signal validated browser signal
     * @param server trusted server-derived context for the same request
     * @return immutable event draft ready for monitoring
     */
    public static SecurityEventDraft toDraft(FrontendSignal signal, FrontendServerContext server) {
        SecurityEventDraft.Builder builder = SecurityEventDraft.builder()
            .eventType("SENSITIVE_VIEW".equalsIgnoreCase(signal.getAction()) ? SecurityEventType.VIEW_SENSITIVE : SecurityEventType.QUERY)
            .action(signal.getAction())
            .result(SecurityEventResult.SUCCESS)
            .sourceIp(server.getSourceIp())
            .requestId(signal.getRequestId())
            .traceId(signal.getTraceId())
            .userId(server.getUserId())
            .accountType(server.getAccountType())
            .roleIds(server.getRoleIds())
            .sessionIdHash(server.getSessionIdHash())
            .deviceIdHash(signal.getDeviceIdHash())
            .resourceType(signal.getResourceType() == null ? "FRONTEND_ROUTE" : signal.getResourceType())
            .resourceId(signal.getResourceIdHash() == null ? signal.getRoute() : signal.getResourceIdHash())
            .occurredAt(signal.getOccurredAt())
            .attribute("frontend_signal", "true")
            .attribute("route", signal.getRoute())
            .attribute("client_event_id", signal.getClientEventId());
        for (java.util.Map.Entry<String, String> entry : signal.getAttributes().entrySet()) {
            builder.attribute(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }
}
