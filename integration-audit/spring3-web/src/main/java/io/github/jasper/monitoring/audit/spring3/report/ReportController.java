package io.github.jasper.monitoring.audit.spring3.report;

import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.action.ResourceAccess;
import io.github.jasper.monitoring.api.fact.ActionFact;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.error.ActionBlockedException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报告业务查询和导出入口。
 *
 * <p>报告资源授权由 {@code @MonitorAction} 的资源阶段在 Controller 前完成，组织范围由宿主
 * resolver 从报告目录解析。真实业务应保持“授权先于查询/导出”的顺序；本类的 URL、
 * 固定响应和夹具导出服务仅用于验收。</p>
 */
@RestController
@RequestMapping("/audit/reports")
public class ReportController {
    private static final long SERVER_REPORTED_ROW_COUNT = 37L;
    private final AuditExportService exports;
    private final AuditReportCatalog reports;

    public ReportController(AuditExportService exports, AuditReportCatalog reports) {
        this.exports = exports;
        this.reports = reports;
    }

    /**
     * 返回授权后的报告最小视图。
     *
     * @param ignored URL 中的报告 ID；实际对象来自授权拦截器
     * @param request 当前请求，用于取得授权结果
     * @return 服务端确认的报告 ID
     */
    @GetMapping("/{reportId}")
    @MonitorAction(BuiltInActions.Query.class)
    @ResourceAccess(requireOrgScope = true)
    public Map<String, Object> report(@ActionFact(BuiltInFacts.ResourceId.class) @PathVariable("reportId") String reportId) {
        AuditReportCatalog.AuditReport report = require(reportId);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("reportId", report.getId());
        return body;
    }

    /**
     * 执行授权后的可观察导出副作用。
     *
     * <p>该路由用来验证跨组织拒绝不会进入业务导出。带风险预检、XLSX 生成和 ReportExport 埋点
     * 的完整链路见 {@link ReportExportController}。</p>
     *
     * @param ignored URL 中的报告 ID；实际对象来自授权拦截器
     * @param request 当前请求，用于取得授权结果
     * @return 固定的服务端行数响应
     */
    @PostMapping("/{reportId}/export")
    @MonitorAction(BuiltInActions.Query.class)
    @ResourceAccess(requireOrgScope = true)
    public ResponseEntity<Map<String, Object>> export(
            @ActionFact(BuiltInFacts.ResourceId.class) @PathVariable("reportId") String reportId) {
        exports.export(require(reportId));
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("rowCount", Long.valueOf(SERVER_REPORTED_ROW_COUNT));
        return ResponseEntity.ok(body);
    }

    private AuditReportCatalog.AuditReport require(String reportId) {
        AuditReportCatalog.AuditReport report = reports.find(reportId);
        if (report == null) throw new IllegalArgumentException("REPORT_NOT_FOUND");
        return report;
    }

    @ExceptionHandler(ActionBlockedException.class)
    public ResponseEntity<Void> blocked(ActionBlockedException ignored) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
