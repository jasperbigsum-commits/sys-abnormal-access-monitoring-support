package io.github.jasper.monitoring.core.application.authorization;

import io.github.jasper.monitoring.core.application.SecurityMonitor;


import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.ResourceScopeAuthorizer;
import io.github.jasper.monitoring.api.ResourceScopeRequest;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * 与框架无关的资源授权桥接器。
 *
 * <p>宿主 {@link ResourceScopeAuthorizer} 始终是授权结论的权威来源。本类只记录最终结论，
 * 绝不会把拒绝改为允许；授权器缺失、返回空值或异常时会按拒绝处理。</p>
 */
public final class ResourceAccessGuard {
    private final ResourceScopeAuthorizer authorizer;
    private final SecurityMonitor monitor;
    private final Clock clock;

    /**
     * 创建资源授权记录桥接器。
     *
     * @param authorizer 宿主拥有的授权决策器，仍是唯一权威来源
     * @param monitor 仅用于审计最终决策的监测入口
     * @param clock 事件时间来源
     */
    public ResourceAccessGuard(ResourceScopeAuthorizer authorizer, SecurityMonitor monitor, Clock clock) {
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.monitor = Objects.requireNonNull(monitor, "monitor");
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
        try {
            decision = authorizer.authorize(identity == null ? IdentityContext.anonymous() : identity, resource);
            if (decision == null) {
                decision = AuthorizationDecision.denied("AUTHORIZATION_NO_DECISION");
            }
        } catch (RuntimeException ignored) {
            decision = AuthorizationDecision.denied("AUTHORIZATION_ERROR");
        }
        recordDecision(identity == null ? IdentityContext.anonymous() : identity, resource, decision);
        return decision;
    }

    private void recordDecision(IdentityContext identity, ResourceScopeRequest resource, AuthorizationDecision decision) {
        try {
            SecurityEventDraft.Builder draft = SecurityEventDraft.builder()
                .eventType(decision.isAllowed() ? SecurityEventType.ACCESS_ALLOWED : SecurityEventType.RESOURCE_SCOPE_DENIED)
                .action(resource.getRequest().getMethod())
                .result(decision.isAllowed() ? SecurityEventResult.SUCCESS : SecurityEventResult.DENIED)
                .sourceIp(resource.getRequest().getSourceIp())
                .requestId(resource.getRequest().getRequestId())
                .traceId(resource.getRequest().getTraceId())
                .userId(identity.getUserId())
                .accountType(identity.getAccountType())
                .roleIds(identity.getRoleIds())
                .sessionIdHash(identity.getSessionIdHash())
                .resourceType(resource.getResourceType())
                .resourceId(resource.getResourceId())
                .orgScope(resource.getOrgScope())
                .reasonCode(decision.getReasonCode())
                .occurredAt(Instant.now(clock));
            monitor.record(draft.build());
        } catch (RuntimeException ignored) {
            // Monitoring failures cannot bypass the host system's established authorization decision.
        }
    }
}
