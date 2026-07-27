package io.github.jasper.monitoring.audit.spring3.security;

import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.audit.spring3.persistence.AuditFixtureRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Reference-host adapter for an independently authenticated rule approver. */
@Component
public final class AuditRuleApproverContext {
    private final HttpServletRequest request;
    private final AuditRbacRealm realm;
    private final AuditFixtureRepository fixtures;

    public AuditRuleApproverContext(HttpServletRequest request, AuditRbacRealm realm,
                                    AuditFixtureRepository fixtures) {
        this.request = request;
        this.realm = realm;
        this.fixtures = fixtures;
    }

    public ManagementActor require(String requesterId) {
        String approverId = request.getHeader("X-Audit-Approver");
        if (!realm.supportsPrincipal(approverId) || requesterId.equals(approverId)
                || !fixtures.findRoles(approverId).contains("audit-approver")) {
            throw new SecurityException("A distinct authenticated rule approver is required");
        }
        return ManagementActor.of(approverId, "audit-spring3-web");
    }
}
