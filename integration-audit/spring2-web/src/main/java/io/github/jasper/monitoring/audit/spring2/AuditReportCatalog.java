package io.github.jasper.monitoring.audit.spring2;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-owned report metadata used by the fixture authorization boundary. */
final class AuditReportCatalog {
    private final Map<String, AuditReport> reports;

    AuditReportCatalog() {
        Map<String, AuditReport> catalog = new LinkedHashMap<String, AuditReport>();
        catalog.put("report-a", new AuditReport("report-a", "org-a"));
        catalog.put("report-b", new AuditReport("report-b", "org-b"));
        reports = Collections.unmodifiableMap(catalog);
    }

    AuditReport find(String reportId) {
        return reports.get(reportId);
    }

    static final class AuditReport {
        private final String id;
        private final String organization;

        private AuditReport(String id, String organization) {
            this.id = id;
            this.organization = organization;
        }

        String getId() {
            return id;
        }

        String getOrganization() {
            return organization;
        }
    }
}
