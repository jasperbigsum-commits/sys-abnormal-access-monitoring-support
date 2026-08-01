package io.github.jasper.monitoring.core.application.authorization;

import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.ResourceScopeAuthorizer;
import io.github.jasper.monitoring.api.ResourceScopeRequest;
import io.github.jasper.monitoring.core.application.MonitoringService;
import java.util.Objects;

/**
 * 与框架无关的资源授权桥接器。
 *
 * <p>宿主 {@link ResourceScopeAuthorizer} 始终是授权结论的权威来源。本类只记录最终结论，
 * 绝不会把拒绝改为允许；授权器缺失、返回空值或异常时会按拒绝处理。</p>
 */
public final class ResourceAccessGuard {
    private final ResourceScopeAuthorizer authorizer;
    private final MonitoringService typedMonitoring;

    /** Creates the strict typed authorization audit bridge. */
    public ResourceAccessGuard(ResourceScopeAuthorizer authorizer, MonitoringService monitoring) {
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.typedMonitoring = Objects.requireNonNull(monitoring, "monitoring");
    }

    /**
     * 获取宿主授权结论，并记录允许或拒绝访问事件。
     *
     * <p>授权器未给出结论或发生异常时将失败关闭（fail closed）为拒绝；监测记录失败不会改变已得出的
     * 宿主授权结论。</p>
     *
     * @param identity 由宿主解析的身份；为 {@code null} 时按匿名请求记录
     * @param resource 请求资源及可信请求元数据
     * @return 宿主授权结论；无法得到可用结论时返回失败关闭的拒绝结果
     */
    public AuthorizationDecision authorize(IdentityContext identity, ResourceScopeRequest resource) {
        AuthorizationDecision decision;
        try {
            decision = authorizer.authorize(identity == null ? IdentityContext.anonymous() : identity, resource);
            if (decision == null) {
                decision = AuthorizationDecision.denied(BuiltInReasonCodes.Authorization.NO_DECISION);
            }
        } catch (RuntimeException ignored) {
            decision = AuthorizationDecision.denied(BuiltInReasonCodes.Authorization.EVALUATION_ERROR);
        }
        recordDecision(identity == null ? IdentityContext.anonymous() : identity, resource, decision);
        return decision;
    }

    private void recordDecision(IdentityContext identity, ResourceScopeRequest resource, AuthorizationDecision decision) {
        try {
            Class<? extends io.github.jasper.monitoring.api.action.ActionType> type = decision.isAllowed()
                ? BuiltInActions.AccessAllowed.class : BuiltInActions.AccessDenied.class;
            ActionFacts.Builder facts = ActionFacts.builder();
            if (resource.getResourceId() != null) {
                facts.put(BuiltInFacts.ResourceId.class, resource.getResourceId());
            }
            ActionOutcome outcome = decision.isAllowed() ? ActionOutcome.success(0L)
                : ActionOutcome.denied(decision.getReason(), 0L);
            typedMonitoring.monitor(ActionExecution.of(type, resource.getRequest(), identity, outcome,
                facts.build(), FactSource.TRUSTED_REQUEST));
        } catch (RuntimeException ignored) {
            // Monitoring failures cannot bypass the host system's established authorization decision.
        }
    }
}
