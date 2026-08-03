package io.github.jasper.monitoring.audit.spring3.report;

import io.github.jasper.monitoring.audit.spring3.security.AuditReportAuthorizationInterceptor;
import io.github.jasper.monitoring.api.error.ActionBlockedException;
import java.util.Collections;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验收夹具 XLSX 导出流程的 HTTP 适配器。
 *
 * <p>The authorization interceptor must have placed the server-loaded report in the request
 * 执行前完成授权。导出被阻断时返回审批状态且不返回工作簿；选择参数格式错误时映射为客户端错误。</p>
 */
@RestController
@RequestMapping("/audit/reports/{reportId}/exports")
public class ReportExportController {
    private final ReportExportService exports;

    public ReportExportController(ReportExportService exports) {
        this.exports = exports;
    }

    /**
     * 接收导出选择并返回审批状态或 XLSX 文件。
     *
     * <p>授权拦截器必须先把服务端报告对象写入请求属性；本方法不从请求体读取组织、身份或最终
     * 行数，也不在 Controller 内直接调用监测组件。</p>
     *
     * @param selection 客户端导出选择
     * @param request 用于读取已授权报告对象
     * @return 202 表示风险预检阻断并等待审批，200 表示已生成文件
     */
    @PostMapping
    @Transactional
    public ResponseEntity<?> export(@RequestBody ReportExportRequest selection, HttpServletRequest request) {
        AuditReportCatalog.AuditReport report = authorizedReport(request);
        ReportExportService.Result result = exports.export(report.getId(), selection);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "report.xlsx");
        return new ResponseEntity<byte[]>(result.getContent(), headers, HttpStatus.OK);
    }
    /**
     * 将导出选择参数校验错误映射为 400。
     *
     * @return 不泄漏内部异常文本的稳定客户端错误
     */
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
