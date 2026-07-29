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

/**
 * 在导出阻断前或工作簿完成后提交权威导出事件的窄适配器。
 *
 * <p>只接受服务端确认的报告 ID、行数、敏感标记和允许结果，并以 {@code HOST_PROVIDER} 提交给
 * {@code MonitoringService}；不会自行执行控制，也不会把监测失败转换为导出放行。</p>
 */
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
