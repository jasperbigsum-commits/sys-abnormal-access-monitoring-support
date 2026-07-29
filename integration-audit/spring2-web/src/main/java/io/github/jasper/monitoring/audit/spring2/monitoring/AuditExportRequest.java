package io.github.jasper.monitoring.audit.spring2.monitoring;

/**
 * 注解式导出验收接口使用的嵌套请求 DTO。
 *
 * <p>对象只用于演示 {@code ActionFact} 的受限属性路径提取；其中的报告、租户和行数都是客户端
 * 输入，不能成为服务端身份、组织范围、授权或最终数据量的依据。</p>
 */
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

    /** 由 Jackson 绑定的嵌套报告选择对象，仅用于参数路径提取示例。 */
    public static class Report {
        private String id;
        private long rows;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public long getRows() { return rows; }

        public void setRows(long rows) { this.rows = rows; }
    }

    /** 由 Jackson 绑定的嵌套租户选择对象，不代表服务端已确认的租户范围。 */
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
