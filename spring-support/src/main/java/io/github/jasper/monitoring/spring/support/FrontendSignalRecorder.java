package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.core.MonitoringOutcome;
import io.github.jasper.monitoring.core.SecurityMonitor;
import io.github.jasper.monitoring.web.FrontendServerContext;
import io.github.jasper.monitoring.web.FrontendSignal;
import io.github.jasper.monitoring.web.FrontendSignalMapper;

/**
 * Host-facing bridge for recording browser telemetry through the canonical web contract.
 * Client data is supplemental: trusted server context remains authoritative for identity and request metadata.
 */
public final class FrontendSignalRecorder {
    private final SecurityMonitor monitor;

    /** @param monitor monitor that persists and evaluates the mapped server-side event */
    public FrontendSignalRecorder(SecurityMonitor monitor) {
        this.monitor = monitor;
    }

    /**
     * Validates and maps browser telemetry into a server-side event before recording it.
     *
     * @param signal client-supplied data restricted by the frontend contract
     * @param serverContext server-resolved request, identity, and timestamp data
     * @return monitoring outcome produced from the canonical event draft
     */
    public MonitoringOutcome record(FrontendSignal signal, FrontendServerContext serverContext) {
        return monitor.record(FrontendSignalMapper.toDraft(signal, serverContext));
    }
}
