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
import io.github.jasper.monitoring.core.application.SecurityEventRecorder;
import io.github.jasper.monitoring.core.port.WhitelistRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 与框架无关的资源授权桥接器。
 *
 * <p>宿主 {@link ResourceScopeAuthorizer} 是基础授权结论的权威来源。只有宿主显式提供可信的
 * 通行证规则和主体范围，并且持久化通行证仍有效时，监测阻断结论才可改为允许。</p>
 */
public final class ResourceAccessGuard {
    private final ResourceScopeAuthorizer authorizer;
    private final SecurityEventRecorder eventRecorder;
    private final String systemId;
    private final WhitelistRepository passes;
    private final Clock clock;

    /** Creates the strict typed authorization audit bridge. */
    /** Creates an authorization bridge that can honor explicitly scoped temporary passes. */
    public ResourceAccessGuard(String systemId, ResourceScopeAuthorizer authorizer, WhitelistRepository passes,
            SecurityEventRecorder eventRecorder, Clock clock) {
        this.systemId = systemId;
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.passes = passes;
        this.eventRecorder = Objects.requireNonNull(eventRecorder, "eventRecorder");
        this.clock = Objects.requireNonNull(clock, "clock");
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
        boolean invalidScope = resource != null && (resource.isScopeResolutionFailed()
            || resource.isOrgScopeRequired() && resource.getOrgScope() == null);
        if (resource != null && resource.isScopeResolutionFailed()) {
            decision = AuthorizationDecision.denied(BuiltInReasonCodes.Authorization.EVALUATION_ERROR);
        } else if (resource == null || resource.getResourceId() == null
                || resource.isOrgScopeRequired() && resource.getOrgScope() == null) {
            decision = AuthorizationDecision.denied(
                BuiltInReasonCodes.Authorization.RESOURCE_SCOPE_DENIED);
        } else try {
            decision = authorizer.authorize(identity == null ? IdentityContext.anonymous() : identity, resource);
            if (decision == null) {
                decision = AuthorizationDecision.denied(BuiltInReasonCodes.Authorization.NO_DECISION);
            }
        } catch (RuntimeException ignored) {
            decision = AuthorizationDecision.denied(BuiltInReasonCodes.Authorization.EVALUATION_ERROR);
        }
        if (!invalidScope && canBeOverridden(decision) && hasActivePass(resource)) {
            decision = AuthorizationDecision.allowed();
        }
        if (resource != null) {
            recordDecision(identity == null ? IdentityContext.anonymous() : identity, resource, decision);
        }
        return decision;
    }

    private static boolean canBeOverridden(AuthorizationDecision decision) {
        if (decision.isAllowed()) return false;
        String reason = decision.getReason().getCode();
        return !BuiltInReasonCodes.Authorization.EVALUATION_ERROR.getCode().equals(reason)
            && !BuiltInReasonCodes.Authorization.NO_DECISION.getCode().equals(reason)
            && !BuiltInReasonCodes.Authorization.AUTHORIZER_NOT_CONFIGURED.getCode().equals(reason);
    }

    private boolean hasActivePass(ResourceScopeRequest resource) {
        if (passes == null || systemId == null || resource == null
                || resource.getPassRuleId() == null || resource.getPassSubject() == null) {
            return false;
        }
        try {
            return passes.isActive(systemId, resource.getPassRuleId(), resource.getPassSubject(), Instant.now(clock));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void recordDecision(IdentityContext identity, ResourceScopeRequest resource, AuthorizationDecision decision) {
        try {
            Class<? extends io.github.jasper.monitoring.api.action.ActionType> type = decision.isAllowed()
                ? BuiltInActions.AccessAllowed.class : BuiltInActions.AccessDenied.class;
            ActionFacts.Builder facts = ActionFacts.builder();
            if (resource.getResourceId() != null) {
                facts.put(BuiltInFacts.ResourceId.class, resource.getResourceId());
            }
            if (resource.getOrgScope() != null) {
                facts.put(BuiltInFacts.OrgScope.class, resource.getOrgScope());
            }
            ActionOutcome outcome = decision.isAllowed() ? ActionOutcome.success(0L)
                : ActionOutcome.denied(decision.getReason(), 0L);
            eventRecorder.record(ActionExecution.of(type, resource.getRequest(), identity, outcome,
                facts.build(), FactSource.TRUSTED_REQUEST));
        } catch (RuntimeException ignored) {
            // Monitoring failures cannot bypass the host system's established authorization decision.
        }
    }
}
