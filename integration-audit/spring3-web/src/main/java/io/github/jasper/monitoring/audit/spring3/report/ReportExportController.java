package io.github.jasper.monitoring.audit.spring3.report;

import io.github.jasper.monitoring.audit.spring3.security.AuditReportAuthorizationInterceptor;
import java.util.Collections;
import jakarta.servlet.http.HttpServletRequest;
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
 * HTTP adapter for the fixture XLSX export flow.
 *
 * <p>The authorization interceptor must have placed the server-loaded report in the request
 * before this method runs. A blocked export returns an approval status and never returns a
 * workbook; malformed selection input is mapped to a client error.</p>
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
        if (result.isBlocked()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Collections.singletonMap("status", "AWAITING_APPROVAL"));
        }
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

    private static AuditReportCatalog.AuditReport authorizedReport(HttpServletRequest request) {
        Object report = request.getAttribute(AuditReportAuthorizationInterceptor.AUTHORIZED_REPORT);
        if (!(report instanceof AuditReportCatalog.AuditReport)) {
            throw new IllegalStateException("Authorized report attribute is missing");
        }
        return (AuditReportCatalog.AuditReport) report;
    }
}
