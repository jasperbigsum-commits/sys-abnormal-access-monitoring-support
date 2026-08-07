package io.github.jasper.monitoring.audit.spring3.report;

import io.github.jasper.monitoring.api.error.ActionBlockedException;
import java.util.Collections;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验收夹具 XLSX 导出流程的 HTTP 适配器。
 *
 * <p>资源授权由导出服务的 {@code @ResourceAccess} 动作阶段在业务执行前完成。</p>
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
     * <p>导出 Service 的资源阶段先解析服务端组织范围；本方法不从请求体读取组织、身份或最终
     * 行数，也不在 Controller 内直接调用监测组件。</p>
     *
     * @param selection 客户端导出选择
     * @param request 用于读取已授权报告对象
     * @return 202 表示风险预检阻断并等待审批，200 表示已生成文件
     */
    @PostMapping
    @Transactional
    public ResponseEntity<?> export(@PathVariable("reportId") String reportId,
                                    @RequestBody ReportExportRequest selection) {
        ReportExportService.Result result = exports.export(reportId, selection);
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

}
