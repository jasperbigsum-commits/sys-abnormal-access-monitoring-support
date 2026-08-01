package io.github.jasper.monitoring.audit.spring3.privilege;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.core.application.MonitoringService;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * 角色授予业务服务和权限变更监测适配器。
 *
 * <p>角色关系仓储先阻止操作者给自己授予权限，再以 {@code HOST_PROVIDER} 提交目标用户、权限增量
 * 和高权限事实。允许或拒绝都记录 PrivilegeChange 事件；真实系统还应接入角色目录和审批策略。</p>
 */
@Service
public final class PrivilegeGrantService {
    private final PrivilegeGrantRepository roles;
    private final MonitoringService monitoring;

    public PrivilegeGrantService(PrivilegeGrantRepository roles, MonitoringService monitoring) {
        this.roles = roles;
        this.monitoring = monitoring;
    }

    public boolean grant(IdentityContext actor, MonitoringRequestContext request,
                         String targetUserId, String roleId) {
        boolean granted = roles.grantUnlessSelf(actor.getUserId(), targetUserId, roleId, Instant.now());
        ActionFacts facts = ActionFacts.builder()
            .put(BuiltInFacts.TargetUserId.class, targetUserId)
            .put(BuiltInFacts.PrivilegeIncrease.class, Boolean.TRUE)
            .put(BuiltInFacts.HighPrivilege.class, Boolean.TRUE)
            .build();
        monitoring.monitor(ActionExecution.of(BuiltInActions.PrivilegeChange.class, request, actor,
            granted ? ActionOutcome.success(0L) : ActionOutcome.denied(
                BuiltInReasonCodes.Privilege.SELF_ESCALATION, 0L),
            facts, FactSource.HOST_PROVIDER));
        return granted;
    }
}
