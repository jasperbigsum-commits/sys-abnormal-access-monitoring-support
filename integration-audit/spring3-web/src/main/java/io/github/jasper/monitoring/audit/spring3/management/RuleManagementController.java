package io.github.jasper.monitoring.audit.spring3.management;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.RuleCatalogService;
import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.command.RuleChangeCommand;
import io.github.jasper.monitoring.api.management.model.RuleView;
import io.github.jasper.monitoring.api.management.query.RuleQuery;
import io.github.jasper.monitoring.api.rule.RuleMode;
import io.github.jasper.monitoring.audit.spring3.security.AuditRuleApproverContext;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 经过双人审批的追加式规则版本 HTTP 适配器。
 *
 * <p>请求只携带规则变更内容和期望版本；当前操作者由服务端身份派生，第二位审批人由独立适配器
 * 验证。规则服务负责模式、阈值、版本、授权、持久化和管理审计，本类不直接改变运行时冻结目录。</p>
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

    @GetMapping
    public ManagementResult<ManagementPage<RuleView>> search(@RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return ManagementResult.ok(rules.search(actor(), RuleQuery.of(
            ManagementHttpParameters.page(page, size, RuleQuery.Sort.ID))));
    }

    @GetMapping("/{id}")
    public ManagementResult<RuleView> get(@PathVariable("id") String id) { return ManagementResult.ok(rules.get(actor(), id)); }

    @PostMapping("/{id}/versions")
    public ManagementResult<RuleView> change(@PathVariable("id") String id, @RequestBody RuleChangeRequest request) {
        ManagementActor requester = actor();
        return ManagementResult.ok(rules.change(requester, approvers.require(requester.getActorId()),
            RuleChangeCommand.of(id, request.getExpectedVersion(), RuleMode.valueOf(request.getMode()),
                request.getThreshold(), request.getReason(), request.getIdempotencyKey())));
    }

    private ManagementActor actor() {
        return ManagementActor.of(contexts.identityContext().getUserId(), "audit-spring3-web");
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
