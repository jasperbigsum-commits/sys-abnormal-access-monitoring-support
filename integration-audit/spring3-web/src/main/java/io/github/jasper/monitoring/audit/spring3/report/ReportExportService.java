package io.github.jasper.monitoring.audit.spring3.report;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.audit.spring3.persistence.AuditFixtureRepository;
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
 * Synchronous export flow used by the Spring3 acceptance host.
 *
 * <p>The service recalculates row count from the fixture repository, applies the
 * export policy, records the monitoring result, and only then generates XLSX.
 * Request fields are selectors; they are not trusted evidence.</p>
 */
@Service
public final class ReportExportService {
    private static final Set<String> ALLOWED_FIELDS = new LinkedHashSet<String>(
        Arrays.asList("rowId", "displayValue", "amount"));
    private static final Set<String> KNOWN_FIELDS = new LinkedHashSet<String>(
        Arrays.asList("rowId", "displayValue", "amount", "sensitiveValue"));

    private final AuditFixtureRepository fixtures;
    private final ExportRiskGuard risks;
    private final ReportExportAuditService audit;
    private final MonitoringContextAccessor contexts;
    private final AtomicInteger workbookInvocations = new AtomicInteger();
    private final Clock clock = Clock.systemUTC();

    public ReportExportService(AuditFixtureRepository fixtures, ExportRiskGuard risks,
                               ReportExportAuditService audit, MonitoringContextAccessor contexts) {
        this.fixtures = fixtures;
        this.risks = risks;
        this.audit = audit;
        this.contexts = contexts;
    }

    public Result export(String reportId, ReportExportRequest request) {
        validate(reportId, request);
        List<String> fields = authorizedFields(request.getFields());
        long rows = fixtures.countReportRows(reportId, request.getMinId(), request.getMaxId(),
            request.getSelectedIds());
        String userId = contexts.identityContext().getUserId();
        ExportRiskGuard.Decision decision = risks.evaluate(userId, rows, request.getFields());
        if (decision.isBlocked()) {
            audit.record(reportId, rows, decision.isSensitive(), false);
            fixtures.recordExport(UUID.randomUUID().toString(), userId, reportId, rows, "DENIED",
                clock.instant());
            return Result.blocked(rows);
        }
        List<Map<String, Object>> data = fixtures.findReportRows(reportId, request.getMinId(),
            request.getMaxId(), request.getSelectedIds());
        byte[] workbook = workbook(fields, data);
        audit.record(reportId, data.size(), false, true);
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
        private final boolean blocked;
        private final byte[] content;
        private final long rows;

        private Result(boolean blocked, byte[] content, long rows) {
            this.blocked = blocked;
            this.content = content;
            this.rows = rows;
        }

        static Result blocked(long rows) {
            return new Result(true, null, rows);
        }

        static Result completed(byte[] content, long rows) {
            return new Result(false, content, rows);
        }

        public boolean isBlocked() {
            return blocked;
        }

        public byte[] getContent() {
            return content;
        }

        public long getRows() {
            return rows;
        }
    }
}
