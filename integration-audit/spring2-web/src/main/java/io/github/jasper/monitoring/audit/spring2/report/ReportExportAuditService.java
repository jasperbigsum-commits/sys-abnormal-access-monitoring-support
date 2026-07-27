package io.github.jasper.monitoring.audit.spring2.report;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.core.application.MonitoringService;
import org.springframework.stereotype.Service;

/** Emits the authoritative export event before a blocked export or after a completed workbook. */
@Service
public final class ReportExportAuditService {
    private final MonitoringService monitoring;
    private final MonitoringContextAccessor contexts;

    public ReportExportAuditService(MonitoringService monitoring, MonitoringContextAccessor contexts) {
        this.monitoring = monitoring;
        this.contexts = contexts;
    }

    public void record(String reportId, long rows, boolean sensitive, boolean allowed) {
        ActionFacts.Builder facts = ActionFacts.builder()
            .put(BuiltInFacts.ResourceId.class, reportId)
            .put(BuiltInFacts.DataCount.class, Long.valueOf(rows));
        if (sensitive) {
            facts.put(BuiltInFacts.Sensitivity.class, "high");
        }
        ActionOutcome outcome = allowed
            ? ActionOutcome.success(0L)
            : ActionOutcome.denied("EXPORT_PREFLIGHT_DENIED", 0L);
        monitoring.monitor(ActionExecution.of(BuiltInActions.ReportExport.class, contexts.requestContext(),
            contexts.identityContext(), outcome, facts.build(), FactSource.HOST_PROVIDER));
    }
}
