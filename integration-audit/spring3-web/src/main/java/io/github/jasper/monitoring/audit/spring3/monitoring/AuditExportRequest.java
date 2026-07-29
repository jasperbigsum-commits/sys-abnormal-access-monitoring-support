package io.github.jasper.monitoring.audit.spring3.monitoring;

/**
 * 注解式导出验收接口使用的嵌套请求 DTO。
 *
 * <p>report.id、report.rows、tenant.code 和顶层 rows 只用于演示参数路径提取。
 * 它们是客户端输入，不是服务端确认的资源、组织或最终数据量；IA-05 通过提交伪造值验证
 * 显式导出入口不会使用这些字段覆盖服务端事实。</p>
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

    /**
     * 请求中的报告选择对象。
     *
     * <p>annotatedExport 使用 report.rows 演示 METHOD_PARAMETER Fact；真实导出完成后的行数
     * 应从业务查询结果计算，不应直接使用该字段。</p>
     */
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

    /**
     * 请求中的租户选择对象。
     *
     * <p>该字段只用于证明客户端可以提交伪造组织值；资源授权和事件中的系统范围必须来自服务端。</p>
     */
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
