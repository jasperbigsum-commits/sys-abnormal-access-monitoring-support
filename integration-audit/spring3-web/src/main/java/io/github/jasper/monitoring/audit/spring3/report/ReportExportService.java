package io.github.jasper.monitoring.audit.spring3.report;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.audit.spring3.persistence.AuditFixtureRepository;
import io.github.jasper.monitoring.spring.support.MonitoringFacts;
import io.github.jasper.monitoring.spring.support.MonitoringGate;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Spring3 验收宿主使用的同步导出流程。
 *
 * <p>服务先从夹具仓储重新统计行数，执行导出策略并记录监测结果，只有允许后才生成 XLSX。
 * 请求字段只是选择条件，不能作为可信事实或授权依据。</p>
 */
@Service
public class ReportExportService {
    private static final Set<String> ALLOWED_FIELDS = new LinkedHashSet<String>(
        Arrays.asList("rowId", "displayValue", "amount"));
    private static final Set<String> KNOWN_FIELDS = new LinkedHashSet<String>(
        Arrays.asList("rowId", "displayValue", "amount", "sensitiveValue"));

    private final AuditFixtureRepository fixtures;
    private final MonitoringContextAccessor contexts;
    private final AtomicInteger workbookInvocations = new AtomicInteger();
    private final Clock clock = Clock.systemUTC();

    public ReportExportService(AuditFixtureRepository fixtures, MonitoringContextAccessor contexts) {
        this.fixtures = fixtures;
        this.contexts = contexts;
    }

    /**
     * 执行导出选择校验、服务端计数、风险中断、监测埋点和文件生成。
     *
     * <p>顺序固定为：校验选择条件 -> 重新统计服务端数据 -> 风险预检 -> 阻断时提交 DENIED 事件
     * 和宿主台账 -> 允许时生成 XLSX -> 成功后提交 SUCCESS 事件和台账。监测组件新产生的控制
     * 通常保护后续请求；当前请求是否继续必须由风险预检和已有控制状态在文件生成前决定。</p>
     *
     * @param reportId 授权拦截器确认后的服务端报告 ID
     * @param request 客户端选择意图，不包含可信行数、组织或授权结论
     * @return 阻断结果或包含已生成 XLSX 内容的成功结果
     */
    @MonitorAction(BuiltInActions.ReportExport.class)
    public Result export(String reportId, ReportExportRequest request) {
        validate(reportId, request);
        List<String> fields = authorizedFields(request.getFields());
        long rows = fixtures.countReportRows(reportId, request.getMinId(), request.getMaxId(),
            request.getSelectedIds());
        String userId = contexts.identityContext().getUserId();
        MonitoringFacts.put(BuiltInFacts.ResourceId.class, reportId);
        MonitoringFacts.put(BuiltInFacts.DataCount.class, rows);
        if (request.getFields() != null && request.getFields().contains("sensitiveValue")) {
            MonitoringFacts.put(BuiltInFacts.Sensitivity.class, BuiltInFacts.SensitivityLevel.HIGH);
        }
        MonitoringGate.checkpoint();
        List<Map<String, Object>> data = fixtures.findReportRows(reportId, request.getMinId(),
            request.getMaxId(), request.getSelectedIds());
        byte[] workbook = workbook(fields, data);
        fixtures.recordExport(UUID.randomUUID().toString(), userId, reportId, data.size(), "SUCCEEDED",
            clock.instant());
        return Result.completed(workbook, data.size());
    }

    /** @return XLSX 生成方法被实际调用的次数，用于验证拒绝发生在文件生成之前 */
    public int getWorkbookInvocationCount() {
        return workbookInvocations.get();
    }

    private byte[] workbook(List<String> fields, List<Map<String, Object>> rows) {
        workbookInvocations.incrementAndGet();
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("report");
            Row header = sheet.createRow(0);
            for (int column = 0; column < fields.size(); column++) {
                header.createCell(column).setCellValue(fields.get(column));
            }
            for (int index = 0; index < rows.size(); index++) {
                Row row = sheet.createRow(index + 1);
                Map<String, Object> values = rows.get(index);
                for (int column = 0; column < fields.size(); column++) {
                    row.createCell(column).setCellValue(String.valueOf(value(values, fields.get(column))));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("XLSX_GENERATION_FAILED", failure);
        }
    }

    private static Object value(Map<String, Object> row, String field) {
        String key = "rowId".equals(field) ? "ROWID"
            : "displayValue".equals(field) ? "DISPLAYVALUE"
            : "amount".equals(field) ? "AMOUNT" : "SENSITIVEVALUE";
        return row.get(key);
    }

    private static List<String> authorizedFields(List<String> requested) {
        LinkedHashSet<String> unique = new LinkedHashSet<String>();
        if (requested != null) {
            for (String field : requested) {
                if (!KNOWN_FIELDS.contains(field)) {
                    throw new IllegalArgumentException("UNKNOWN_EXPORT_FIELD");
                }
                if (ALLOWED_FIELDS.contains(field)) {
                    unique.add(field);
                }
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("NO_AUTHORIZED_EXPORT_FIELDS");
        }
        return new java.util.ArrayList<String>(unique);
    }

    private static void validate(String reportId, ReportExportRequest request) {
        if (reportId == null || reportId.trim().isEmpty() || request == null) {
            throw new IllegalArgumentException("REPORT_REQUIRED");
        }
        if (request.getMinId() != null && request.getMaxId() != null
                && request.getMinId().longValue() > request.getMaxId().longValue()) {
            throw new IllegalArgumentException("INVALID_EXPORT_RANGE");
        }
    }

    public static final class Result {
        private final byte[] content;
        private final long rows;

        private Result(byte[] content, long rows) {
            this.content = content;
            this.rows = rows;
        }

        static Result completed(byte[] content, long rows) {
            return new Result(content, rows);
        }

        public byte[] getContent() {
            return content;
        }

        public long getRows() {
            return rows;
        }
    }
}
