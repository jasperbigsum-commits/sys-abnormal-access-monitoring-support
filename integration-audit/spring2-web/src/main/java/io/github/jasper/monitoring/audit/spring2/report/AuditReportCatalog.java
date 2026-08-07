package io.github.jasper.monitoring.audit.spring2.report;

import java.util.Map;
import io.github.jasper.monitoring.audit.spring2.persistence.AuditFixtureRepository;
import org.springframework.stereotype.Component;

/**
 * 参考宿主持有的报告元数据目录。
 *
 * <p>只返回服务端确认的报告 ID 和组织范围，供资源 resolver 使用；生产接入应替换为真实报告、
 * 数据权限或租户服务，不能复用验收夹具表。</p>
 */
@Component
public final class AuditReportCatalog {
    private final AuditFixtureRepository fixtures;

    public AuditReportCatalog(AuditFixtureRepository fixtures) {
        this.fixtures = fixtures;
    }

    /**
     * 按服务端报告 ID 查询报告元数据。
     *
     * @param reportId 服务端路由解析出的报告 ID
     * @return 存在时返回报告及组织范围，不存在时返回 {@code null}
     */
    public AuditReport find(String reportId) {
        Map<String, Object> report = fixtures.findReport(reportId);
        return report.isEmpty() ? null : new AuditReport(
            String.valueOf(report.get("REPORTID")), String.valueOf(report.get("ORGANIZATIONID")));
    }

    public static final class AuditReport {
        private final String id;
        private final String organization;

        AuditReport(String id, String organization) {
            this.id = id;
            this.organization = organization;
        }

        public String getId() { return id; }
        public String getOrganization() { return organization; }
    }
}
