package io.github.jasper.monitoring.core.application.authentication;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.action.ActionDisposition;
import io.github.jasper.monitoring.api.action.ActionFailurePolicy;
import io.github.jasper.monitoring.api.action.ActionRequirement;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.authentication.AuthenticationMonitor;
import io.github.jasper.monitoring.api.authentication.AuthenticationStage;
import io.github.jasper.monitoring.api.authentication.LoginSubjectInput;
import io.github.jasper.monitoring.api.code.ReasonCode;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringFailure;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.event.FailureClass;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.port.AuthenticationControlRepository;
import io.github.jasper.monitoring.core.port.WhitelistRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link AuthenticationMonitor} 的默认实现。
 *
 * <p>实现负责生成稳定的 opaque 登录主体、查询账号与来源 IP 上的活动控制，并把认证结果
 * 转换为强类型登录事件。调用方不会接触派生主体或持久化结构。监测记录失败只写入脱敏诊断
 * 信息，不覆盖宿主认证流程的最终业务结果；控制预检失败则按配置的失败策略决定放行或阻断。</p>
 */
public final class DefaultAuthenticationMonitor implements AuthenticationMonitor {
    private static final Logger LOGGER = Logger.getLogger(DefaultAuthenticationMonitor.class.getName());
    private final String systemId;
    private final LoginSubjectKeyFactory keys;
    private final AuthenticationControlRepository controls;
    private final WhitelistRepository passes;
    private final MonitoringService monitoring;
    private final MonitoringContextAccessor context;
    private final Clock clock;
    private final ActionFailurePolicy controlFailurePolicy;

    /**
     * 创建默认认证监测门面。
     *
     * @param systemId 监测数据所属的稳定系统标识
     * @param keys opaque 登录主体密钥工厂
     * @param controls 活动认证控制查询仓储
     * @param monitoring 强类型监测服务
     * @param context 当前请求上下文访问器
     * @param clock 计算控制有效期和事件时间使用的时钟
     * @param controlFailurePolicy 控制仓储不可用时的失败策略
     * @throws NullPointerException 任一必需协作者为空时抛出
     * @throws IllegalArgumentException {@code systemId} 为空白时抛出
     */
    public DefaultAuthenticationMonitor(String systemId, LoginSubjectKeyFactory keys,
            AuthenticationControlRepository controls, MonitoringService monitoring,
            MonitoringContextAccessor context, Clock clock, ActionFailurePolicy controlFailurePolicy) {
        this(systemId, keys, controls, null, monitoring, context, clock, controlFailurePolicy);
    }

    /** Creates a pass-aware authentication monitor. */
    public DefaultAuthenticationMonitor(String systemId, LoginSubjectKeyFactory keys,
            AuthenticationControlRepository controls, WhitelistRepository passes, MonitoringService monitoring,
            MonitoringContextAccessor context, Clock clock, ActionFailurePolicy controlFailurePolicy) {
        if (systemId == null || systemId.trim().isEmpty()) {
            throw new IllegalArgumentException("systemId is required");
        }
        this.systemId = systemId.trim();
        this.keys = Objects.requireNonNull(keys, "keys");
        this.controls = Objects.requireNonNull(controls, "controls");
        this.passes = passes;
        this.monitoring = Objects.requireNonNull(monitoring, "monitoring");
        this.context = Objects.requireNonNull(context, "context");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.controlFailurePolicy = Objects.requireNonNull(controlFailurePolicy, "controlFailurePolicy");
    }

    /** {@inheritDoc} */
    @Override
    public ActionDecision preCheck(LoginSubjectInput subject) {
        String key = keys.generate(subject);
        MonitoringRequestContext request = context.requestContext();
        Instant at = Instant.now(clock);
        try {
            Set<ControlActionType> active = EnumSet.noneOf(ControlActionType.class);
            Set<String> ruleIds = new LinkedHashSet<String>();
            add(controls.findActive(systemId, key, at), active, ruleIds, at);
            add(controls.findActive(systemId, "ip:" + request.getSourceIp(), at), active, ruleIds, at);
            return decision(active, ruleIds);
        } catch (RuntimeException failure) {
            log(failure, request);
            return controlFailurePolicy == ActionFailurePolicy.FAIL_CLOSED
                ? ActionDecision.blocked("MONITORING-CONTROL-UNAVAILABLE") : ActionDecision.allow();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void recordDenied(LoginSubjectInput subject, AuthenticationStage stage, ReasonCode reason) {
        record(subject, stage, IdentityContext.anonymous(), ActionOutcome.denied(reason, 0L));
    }

    /** {@inheritDoc} */
    @Override
    public void recordFailure(LoginSubjectInput subject, AuthenticationStage stage,
            ReasonCode reason, FailureClass failureClass) {
        record(subject, stage, IdentityContext.anonymous(), ActionOutcome.failure(reason, failureClass, 0L));
    }

    /** {@inheritDoc} */
    @Override
    public void recordSuccess(LoginSubjectInput subject, IdentityContext authenticatedIdentity) {
        Objects.requireNonNull(authenticatedIdentity, "authenticatedIdentity");
        if (authenticatedIdentity.getAccountType() == AccountType.ANONYMOUS
                || authenticatedIdentity.getUserId() == null
                || authenticatedIdentity.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("recordSuccess requires an authenticated identity");
        }
        record(subject, AuthenticationStage.CREDENTIAL, authenticatedIdentity, ActionOutcome.success(0L));
    }

    private void record(LoginSubjectInput subject, AuthenticationStage stage,
            IdentityContext identity, ActionOutcome outcome) {
        MonitoringRequestContext request = context.requestContext();
        try {
            ActionFacts facts = ActionFacts.builder()
                .put(BuiltInFacts.LoginSubjectKey.class, keys.generate(subject))
                .put(BuiltInFacts.AuthenticationStageFact.class, Objects.requireNonNull(stage, "stage"))
                .build();
            monitoring.monitor(ActionExecution.of(BuiltInActions.Login.class, request, identity, outcome,
                facts, FactSource.FRAMEWORK_OUTCOME));
        } catch (RuntimeException failure) {
            log(failure, request);
        }
    }

    private void add(List<ControlCommand> commands, Set<ControlActionType> active, Set<String> ruleIds, Instant at) {
        for (ControlCommand command : commands) {
            if (passes != null && command.getRuleId() != null
                    && passes.isActive(systemId, command.getRuleId(), command.getSubject(), at)) {
                continue;
            }
            active.add(command.getAction());
            if (command.getRuleId() != null && !command.getRuleId().trim().isEmpty()) {
                ruleIds.add(command.getRuleId());
            }
        }
    }

    private static ActionDecision decision(Set<ControlActionType> active, Set<String> ruleIds) {
        Set<ActionRequirement> requirements = EnumSet.noneOf(ActionRequirement.class);
        if (active.contains(ControlActionType.REQUIRE_MFA)) requirements.add(ActionRequirement.MFA);
        if (active.contains(ControlActionType.REQUIRE_CAPTCHA)) requirements.add(ActionRequirement.CAPTCHA);
        boolean blocked = active.contains(ControlActionType.LOCK)
            || active.contains(ControlActionType.DENY)
            || active.contains(ControlActionType.RATE_LIMIT);
        return ActionDecision.of(blocked ? ActionDisposition.BLOCK : ActionDisposition.ALLOW,
            requirements, active, ruleIds);
    }

    private static void log(RuntimeException failure, MonitoringRequestContext request) {
        MonitoringErrorCode code = failure instanceof MonitoringFailure
            ? ((MonitoringFailure) failure).getErrorCode()
            : MonitoringErrorCode.MONITORING_SYSTEM_UNAVAILABLE;
        LOGGER.log(Level.WARNING, "Authentication monitoring failed [code={0}, requestId={1}, traceId={2}]",
            new Object[] {code.getCode(), request.getRequestId(), request.getTraceId()});
    }
}
