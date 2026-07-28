package io.github.jasper.monitoring.audit.spring3.security;

import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.audit.spring3.persistence.AuditFixtureRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 参考宿主中独立规则审批人的身份适配器。
 *
 * <p>这是集成夹具实现：审批人 Header 和固定角色仅用于验证双人审批边界；生产应从独立可信认证上下文派生审批人。</p>
 */
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
        // 集成夹具实现：测试请求用固定 Header 传递第二位审批人。
        String approverId = request.getHeader("X-Audit-Approver");
        if (!realm.supportsPrincipal(approverId) || requesterId.equals(approverId)
                || !fixtures.findRoles(approverId).contains("audit-approver")) {
            throw new SecurityException("A distinct authenticated rule approver is required");
        }
        return ManagementActor.of(approverId, "audit-spring3-web");
    }
}
