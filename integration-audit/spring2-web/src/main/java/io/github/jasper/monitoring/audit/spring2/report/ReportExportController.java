package io.github.jasper.monitoring.audit.spring2.report;

import io.github.jasper.monitoring.audit.spring2.security.AuditReportAuthorizationInterceptor;
import io.github.jasper.monitoring.api.error.ActionBlockedException;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 可直接挂接 Controller 的 XLSX 导出适配器。
 *
 * <p>授权拦截器必须先放入服务端报告对象；风险阻断返回审批状态且不生成文件，参数错误返回客户端
 * 错误。前端 URL、响应协议和真实导出实现仍由宿主负责。</p>
 */
@RestController
@RequestMapping("/audit/reports/{reportId}/exports")
public class ReportExportController {
    private final ReportExportService exports;

    public ReportExportController(ReportExportService exports) {
        this.exports = exports;
    }

    @PostMapping
    public ResponseEntity<?> export(@RequestBody ReportExportRequest selection, HttpServletRequest request) {
        AuditReportCatalog.AuditReport report = authorizedReport(request);
        ReportExportService.Result result = exports.export(report.getId(), selection);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "report.xlsx");
        return new ResponseEntity<byte[]>(result.getContent(), headers, HttpStatus.OK);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> invalid(IllegalArgumentException ignored) {
        return ResponseEntity.badRequest()
            .body(Collections.singletonMap("status", "INVALID_EXPORT_REQUEST"));
    }

    @ExceptionHandler(ActionBlockedException.class)
    public ResponseEntity<?> blocked(ActionBlockedException ignored) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(Collections.singletonMap("status", "AWAITING_APPROVAL"));
    }

    private static AuditReportCatalog.AuditReport authorizedReport(HttpServletRequest request) {
        Object report = request.getAttribute(AuditReportAuthorizationInterceptor.AUTHORIZED_REPORT);
        if (!(report instanceof AuditReportCatalog.AuditReport)) {
            throw new IllegalStateException("Authorized report attribute is missing");
        }
        return (AuditReportCatalog.AuditReport) report;
    }
}
