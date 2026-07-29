package io.github.jasper.monitoring.audit.spring2.report;

import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.spring.support.MonitoringRecorder;
import org.springframework.stereotype.Service;

/**
 * 在导出阻断前或工作簿完成后提交权威导出事件的窄适配器。
 *
 * <p>只接受服务端确认的报告 ID、行数、敏感标记和允许结果，并通过
 * {@code MonitoringRecorder} 以 {@code HOST_PROVIDER} 提交；不会自行执行控制，也不会把监测失败转换为导出放行。</p>
 */
@Service
public final class ReportExportAuditService {
    private final MonitoringRecorder monitoringRecorder;

    public ReportExportAuditService(MonitoringRecorder monitoringRecorder) {
        this.monitoringRecorder = monitoringRecorder;
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
        monitoringRecorder.record(BuiltInActions.ReportExport.class, outcome, facts.build());
    }
}
