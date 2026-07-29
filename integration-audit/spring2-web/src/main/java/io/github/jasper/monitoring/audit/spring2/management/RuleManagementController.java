package io.github.jasper.monitoring.audit.spring2.management;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.RuleCatalogService;
import io.github.jasper.monitoring.api.management.command.RuleChangeCommand;
import io.github.jasper.monitoring.api.management.model.RuleView;
import io.github.jasper.monitoring.api.rule.RuleMode;
import io.github.jasper.monitoring.audit.spring2.security.AuditRuleApproverContext;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 经过审批的追加式规则版本 HTTP 适配器。
 *
 * <p>请求只携带规则变更内容和期望版本，当前操作者和审批人由服务端身份适配器确认；规则服务
 * 负责规则校验、版本、授权、持久化和管理审计。</p>
 */
@RestController
@RequestMapping("/audit/management/rules")
public class RuleManagementController {
    private final MonitoringContextAccessor contexts;
    private final RuleCatalogService rules;
    private final AuditRuleApproverContext approvers;

    public RuleManagementController(MonitoringContextAccessor contexts, RuleCatalogService rules,
                                    AuditRuleApproverContext approvers) {
        this.contexts = contexts;
        this.rules = rules;
        this.approvers = approvers;
    }

    @PostMapping("/{id}/versions")
    public RuleView change(@PathVariable("id") String id, @RequestBody RuleChangeRequest request) {
        ManagementActor requester = actor();
        return rules.change(requester, approvers.require(requester.getActorId()),
            RuleChangeCommand.of(id, request.getExpectedVersion(), RuleMode.valueOf(request.getMode()),
                request.getThreshold(), request.getReason(), request.getIdempotencyKey()));
    }

    private ManagementActor actor() {
        return ManagementActor.of(contexts.identityContext().getUserId(), "audit-spring2-web");
    }

    public static final class RuleChangeRequest {
        private long expectedVersion;
        private String mode;
        private long threshold;
        private String reason;
        private String idempotencyKey;
        public long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(long value) { expectedVersion = value; }
        public String getMode() { return mode; }
        public void setMode(String value) { mode = value; }
        public long getThreshold() { return threshold; }
        public void setThreshold(long value) { threshold = value; }
        public String getReason() { return reason; }
        public void setReason(String value) { reason = value; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String value) { idempotencyKey = value; }
    }
}
