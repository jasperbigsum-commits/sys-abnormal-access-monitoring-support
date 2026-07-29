package io.github.jasper.monitoring.audit.spring3.report;

import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.spring.support.MonitoringRecorder;
import org.springframework.stereotype.Service;

/**
 * 在导出阻断前或文件生成完成后提交权威导出事件。
 *
 * <p>本类通过 MonitoringRecorder 适配宿主业务结果：它只接收已经完成授权、
 * 计数和风险判断的 reportId、rows、sensitive 和 allowed，并统一使用 HOST_PROVIDER 记录来源。
 * 阻断路径必须先调用本类再返回审批状态；成功路径必须在 XLSX 生成成功后调用。</p>
 */
@Service
public final class ReportExportAuditService {
    private final MonitoringRecorder monitoringRecorder;

    public ReportExportAuditService(MonitoringRecorder monitoringRecorder) {
        this.monitoringRecorder = monitoringRecorder;
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
        // 该调用是 report 业务到组件的唯一监测适配点；规则命中后的控制由宿主处理器落地。
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
