package io.github.jasper.monitoring.audit.spring2.report;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.fact.ActionFact;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.audit.spring2.persistence.AuditFixtureRepository;
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
 * 参考宿主的同步导出业务和监测事实提交边界。
 *
 * <p>服务端重新统计行数并执行风险预检；阻断时先提交拒绝导出事件且不生成文件，成功时在文件
 * 生成完成后提交成功事件。请求体只表达选择意图，不能作为组织、授权或最终行数事实。</p>
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
