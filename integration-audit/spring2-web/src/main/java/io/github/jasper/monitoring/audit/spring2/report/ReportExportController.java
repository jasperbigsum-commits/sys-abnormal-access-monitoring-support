package io.github.jasper.monitoring.audit.spring2.report;

import io.github.jasper.monitoring.api.error.ActionBlockedException;
import java.util.Collections;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 可直接挂接 Controller 的 XLSX 导出适配器。
 *
 * <p>导出 Service 的资源阶段先解析服务端组织范围；风险阻断返回审批状态且不生成文件，参数错误
 * 返回客户端错误。前端 URL、响应协议和真实导出实现仍由宿主负责。</p>
 */
@RestController
@RequestMapping("/audit/reports/{reportId}/exports")
public class ReportExportController {
    private final ReportExportService exports;

    public ReportExportController(ReportExportService exports) {
        this.exports = exports;
    }

    @PostMapping
    public ResponseEntity<?> export(@PathVariable("reportId") String reportId,
                                    @RequestBody ReportExportRequest selection) {
        ReportExportService.Result result = exports.export(reportId, selection);
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

}
