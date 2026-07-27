package io.github.jasper.monitoring.audit.spring2.report;

import java.util.Map;
import io.github.jasper.monitoring.audit.spring2.persistence.AuditFixtureRepository;
import org.springframework.stereotype.Component;

/** Server-owned report metadata used by the reference host. */
@Component
public final class AuditReportCatalog {
    private final AuditFixtureRepository fixtures;

    public AuditReportCatalog(AuditFixtureRepository fixtures) {
        this.fixtures = fixtures;
    }

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
