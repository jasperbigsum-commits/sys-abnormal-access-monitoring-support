package io.github.jasper.monitoring.audit.spring3.report;

import io.github.jasper.monitoring.audit.spring3.security.AuditReportAuthorizationInterceptor;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报告业务查询和导出入口。
 *
 * <p>报告资源授权由宿主拦截器在 Controller 前完成，Controller 只消费拦截器放入的已授权报告对象，
 * 不再次相信路径中的资源或组织信息。真实业务应保持“授权先于查询/导出”的顺序；本类的 URL、
 * 固定响应和夹具导出服务仅用于验收。</p>
 */
@RestController
@RequestMapping("/audit/reports")
public class ReportController {
    private static final long SERVER_REPORTED_ROW_COUNT = 37L;
    private final AuditExportService exports;

    public ReportController(AuditExportService exports) {
        this.exports = exports;
    }

    /**
     * 返回授权后的报告最小视图。
     *
     * @param ignored URL 中的报告 ID；实际对象来自授权拦截器
     * @param request 当前请求，用于取得授权结果
     * @return 服务端确认的报告 ID
     */
    @GetMapping("/{reportId}")
    public Map<String, Object> report(@PathVariable("reportId") String ignored, HttpServletRequest request) {
        AuditReportCatalog.AuditReport report = authorizedReport(request);
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
