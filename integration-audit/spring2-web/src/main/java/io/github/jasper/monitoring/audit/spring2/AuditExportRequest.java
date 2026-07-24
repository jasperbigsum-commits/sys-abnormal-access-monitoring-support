package io.github.jasper.monitoring.audit.spring2;

/** Request body used by the annotated export acceptance endpoints. */
public class AuditExportRequest {
    private Report report;
    private Tenant tenant;
    private long rows;

    public Report getReport() {
        return report;
    }

    public void setReport(Report report) {
        this.report = report;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public long getRows() {
        return rows;
    }

    public void setRows(long rows) {
        this.rows = rows;
    }

    /** Nested report reference bound by Jackson. */
    public static class Report {
        private String id;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    /** Nested tenant reference bound by Jackson. */
    public static class Tenant {
        private String code;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }
}
