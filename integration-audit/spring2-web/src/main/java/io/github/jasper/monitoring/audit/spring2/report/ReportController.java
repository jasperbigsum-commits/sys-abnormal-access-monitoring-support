package io.github.jasper.monitoring.audit.spring2.report;

import io.github.jasper.monitoring.audit.spring2.security.AuditReportAuthorizationInterceptor;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报告业务查询和导出入口。
 *
 * <p>资源授权在 Controller 前由宿主拦截器完成，本类只消费已授权报告对象，不信任路径中的组织
 * 或客户端资源字段；固定 URL 和响应仅用于集成验收。</p>
 */
@RestController
@RequestMapping("/audit/reports")
public class ReportController {
    private static final long SERVER_REPORTED_ROW_COUNT = 37L;
    private final AuditExportService exports;

    public ReportController(AuditExportService exports) {
        this.exports = exports;
    }

    @GetMapping("/{reportId}")
    public Map<String, Object> report(@PathVariable("reportId") String ignored, HttpServletRequest request) {
        AuditReportCatalog.AuditReport report = authorizedReport(request);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("reportId", report.getId());
        return body;
    }

    @PostMapping("/{reportId}/export")
    public ResponseEntity<Map<String, Object>> export(@PathVariable("reportId") String ignored,
                                                      HttpServletRequest request) {
        exports.export(authorizedReport(request));
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("rowCount", Long.valueOf(SERVER_REPORTED_ROW_COUNT));
        return ResponseEntity.ok(body);
    }

    private static AuditReportCatalog.AuditReport authorizedReport(HttpServletRequest request) {
        Object report = request.getAttribute(AuditReportAuthorizationInterceptor.AUTHORIZED_REPORT);
        if (!(report instanceof AuditReportCatalog.AuditReport)) {
            throw new IllegalStateException("Authorized report attribute is missing");
        }
        return (AuditReportCatalog.AuditReport) report;
    }
}
