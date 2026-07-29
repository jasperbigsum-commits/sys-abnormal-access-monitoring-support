package io.github.jasper.monitoring.audit.spring3.report;

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
 * 在导出阻断前或文件生成完成后提交权威导出事件。
 *
 * <p>本类是宿主业务结果与组件 MonitoringService 之间的窄适配层：它只接收已经完成授权、
 * 计数和风险判断的 reportId、rows、sensitive 和 allowed，并统一使用 HOST_PROVIDER 记录来源。
 * 阻断路径必须先调用本类再返回审批状态；成功路径必须在 XLSX 生成成功后调用。</p>
 */
@Service
public final class ReportExportAuditService {
    private final MonitoringService monitoring;
    private final MonitoringContextAccessor contexts;

    public ReportExportAuditService(MonitoringService monitoring, MonitoringContextAccessor contexts) {
        this.monitoring = monitoring;
        this.contexts = contexts;
    }

    /**
     * 记录一次导出预检或完成结果。
     *
     * @param reportId 服务端解析出的报告 ID
     * @param rows 服务端统计的涉及行数
     * @param sensitive 服务端判定的敏感标记
     * @param allowed 是否允许继续生成文件
     */
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
