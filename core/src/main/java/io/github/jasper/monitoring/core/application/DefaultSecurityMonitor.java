package io.github.jasper.monitoring.core.application;

import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.port.ControlHandler;
import io.github.jasper.monitoring.core.port.MonitoringRepository;
import io.github.jasper.monitoring.core.application.control.ControlHandlerRegistry;
import io.github.jasper.monitoring.core.domain.SecurityAlert;


import io.github.jasper.monitoring.core.domain.ControlExecution;


import io.github.jasper.monitoring.core.port.NotificationChannel;
import io.github.jasper.monitoring.core.domain.SecurityEvent;



import io.github.jasper.monitoring.core.domain.RuleMatch;


import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.EventInputStatus;
import io.github.jasper.monitoring.api.EventInputValidation;
import io.github.jasper.monitoring.api.MonitoringEventPolicy;
import io.github.jasper.monitoring.api.MonitoringInputIssueReporter;
import io.github.jasper.monitoring.api.MonitoringMode;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import io.github.jasper.monitoring.core.application.quality.DefaultMonitoringEventPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 编排事件持久化、确定性检测、告警生成与可选控制执行的默认监测实现。
 *
 * <p>在 {@code ENFORCE} 模式下，未配置至少一个可执行动作的宿主 {@link ControlHandler} 时会拒绝创建；
 * 这可避免只有规则命中而无实际执行能力时意外开启控制模式。</p>
 */
public final class DefaultSecurityMonitor implements SecurityMonitor {
    private final String systemId;
    private final Clock clock;
    private final MonitoringRepository repository;
    private final List<DetectionRule> rules;
    private final MonitoringMode mode;
    private final DefaultAlertService alertService;
    private final DefaultControlService controlService;
    private final NotificationChannel notificationChannel;
    private final MonitoringEventPolicy eventPolicy;
    private final MonitoringInputIssueReporter inputIssueReporter;
    private final Set<String> enabledRuleIds;

    /**
     * 创建默认监测器。
     *
     * @param systemId 写入全部事件的稳定系统标识
     * @param clock 服务端时间来源
     * @param repository 监测状态的持久化端口
     * @param rules 每条事件均会评估的确定性规则
     * @param mode 仅观察模式或执行宿主控制动作的模式
     * @param handlers 宿主控制动作实现；执行模式下必须至少支持一种可执行动作
     * @param notifications 尽力而为的告警通知通道
     * @throws IllegalStateException 执行模式未配置可用宿主控制处理器时
     */
    public DefaultSecurityMonitor(String systemId, Clock clock, MonitoringRepository repository, List<DetectionRule> rules,
                                  MonitoringMode mode, ControlHandlerRegistry handlers, NotificationChannel notifications) {
        this(systemId, clock, repository, rules, mode, handlers, notifications,
            new DefaultMonitoringEventPolicy(), MonitoringInputIssueReporter.noop());
    }

    /**
     * 创建可替换规则输入策略与诊断报告器的默认监测器。
     *
     * <p>报告器仅接收稳定诊断，并在监测事务提交后尽力执行；它无法影响规则、告警或控制结果。</p>
     *
     * @param systemId 写入全部事件的稳定系统标识
     * @param clock 服务端时间来源
     * @param repository 监测状态的持久化端口
     * @param rules 每条事件均会评估的确定性规则
     * @param mode 仅观察模式或执行宿主控制动作的模式
     * @param handlers 宿主控制动作实现；执行模式下必须至少支持一种可执行动作
     * @param notifications 尽力而为的告警通知通道
     * @param eventPolicy 规则资格校验策略
     * @param inputIssueReporter 已提交事件的输入质量诊断报告器
     */
    public DefaultSecurityMonitor(String systemId, Clock clock, MonitoringRepository repository, List<DetectionRule> rules,
                                  MonitoringMode mode, ControlHandlerRegistry handlers, NotificationChannel notifications,
                                  MonitoringEventPolicy eventPolicy, MonitoringInputIssueReporter inputIssueReporter) {
        if (systemId == null || systemId.trim().isEmpty()) {
            throw new MonitoringValidationException(MonitoringErrorCode.REQUIRED_FIELD_MISSING,
                "systemId is required");
        }
        this.systemId = systemId;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.rules = new ArrayList<DetectionRule>(rules);
        this.enabledRuleIds = enabledRuleIds(this.rules);
        this.mode = Objects.requireNonNull(mode, "mode");
        if (this.mode == MonitoringMode.ENFORCE && handlers.isEmpty()) {
            throw new MonitoringConfigurationException(MonitoringErrorCode.ENFORCEMENT_HANDLER_REQUIRED,
                "ENFORCE mode requires at least one host ControlHandler");
        }
        this.alertService = new DefaultAlertService(repository, clock);
        this.controlService = new DefaultControlService(repository, handlers, clock);
        this.notificationChannel = Objects.requireNonNull(notifications, "notifications");
        this.eventPolicy = Objects.requireNonNull(eventPolicy, "eventPolicy");
        this.inputIssueReporter = Objects.requireNonNull(inputIssueReporter, "inputIssueReporter");
    }

    /**
     * 先持久化事件，再评估规则，确保匹配逻辑始终包含当前观测。
     *
     * <p>控制处理失败会作为结果的一部分返回，不会直接抛入宿主业务流程；仓储或规则本身的运行时
     * 失败仍会向调用方传播，由边界适配层决定是否隔离。</p>
     *
     * @param draft 已由宿主服务端校验的事件草稿
     * @return 本次事件产生的全部监测结果
     */
    @Override
    public MonitoringOutcome record(SecurityEventDraft draft) {
        return record(draft, EventInputValidation.valid());
    }

    /**
     * 将边界适配器的稳定诊断与规则输入策略结论合并后再持久化和评估。
     *
     * <p>边界诊断的伪规则标识不会匹配实际检测规则，因此它会标记事件输入不完整，但不会阻止
     * 无关规则运行。</p>
     */
    @Override
    public MonitoringOutcome record(SecurityEventDraft draft, EventInputValidation externalValidation) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(externalValidation, "externalValidation");
        EventInputValidation policyValidation = eventPolicy.validate(draft, enabledRuleIds);
        EventInputValidation validation = mergeValidations(policyValidation, externalValidation);
        SecurityEvent event = SecurityEvent.from(draft, systemId, UUID.randomUUID().toString(), Instant.now(clock), validation);
        PersistenceResult persistence = repository.inTransaction(() -> persist(event, validation));
        reportInputIssues(draft, validation);
        notifyAlerts(persistence.alerts);
        List<ControlExecution> controls = executeControls(persistence.matches, persistence.alerts);
        return new MonitoringOutcome(event, persistence.matches, persistence.alerts, controls);
    }

    private static EventInputValidation mergeValidations(EventInputValidation policyValidation,
                                                          EventInputValidation externalValidation) {
        Set<io.github.jasper.monitoring.api.EventInputIssue> uniqueIssues =
            new LinkedHashSet<io.github.jasper.monitoring.api.EventInputIssue>();
        uniqueIssues.addAll(policyValidation.getIssues());
        uniqueIssues.addAll(externalValidation.getIssues());
        List<io.github.jasper.monitoring.api.EventInputIssue> issues =
            new ArrayList<io.github.jasper.monitoring.api.EventInputIssue>(uniqueIssues);
        Set<String> ineligibleRuleIds = new LinkedHashSet<String>();
        ineligibleRuleIds.addAll(policyValidation.getIneligibleRuleIds());
        ineligibleRuleIds.addAll(externalValidation.getIneligibleRuleIds());
        EventInputStatus status = mergedStatus(policyValidation.getStatus(), externalValidation.getStatus());
        if (issues.isEmpty()) {
            return status == EventInputStatus.VALID ? EventInputValidation.valid()
                : EventInputValidation.of(status, Collections.<io.github.jasper.monitoring.api.EventInputIssue>emptyList(),
                    Collections.<String>emptySet());
        }
        return EventInputValidation.of(status, issues, ineligibleRuleIds);
    }

    private static EventInputStatus mergedStatus(EventInputStatus policyStatus, EventInputStatus externalStatus) {
        if (policyStatus == EventInputStatus.INVALID || externalStatus == EventInputStatus.INVALID) {
            return EventInputStatus.INVALID;
        }
        if (policyStatus == EventInputStatus.INCOMPLETE || externalStatus == EventInputStatus.INCOMPLETE) {
            return EventInputStatus.INCOMPLETE;
        }
        if (policyStatus == EventInputStatus.UNKNOWN || externalStatus == EventInputStatus.UNKNOWN) {
            return EventInputStatus.UNKNOWN;
        }
        return EventInputStatus.VALID;
    }

    private PersistenceResult persist(SecurityEvent event, EventInputValidation validation) {
        repository.saveEvent(event);
        List<SecurityEvent> history = canonicalHistory(event, repository.findEventsSince(
            event.getOccurredAt().minus(Duration.ofDays(1))));
        List<RuleMatch> matches = new ArrayList<RuleMatch>();
        List<SecurityAlert> alerts = new ArrayList<SecurityAlert>();
        for (DetectionRule rule : rules) {
            if (validation.getIneligibleRuleIds().contains(rule.getRuleId())) {
                continue;
            }
            Optional<RuleMatch> match = rule.evaluate(event, history);
            if (!match.isPresent()) { continue; }
            RuleMatch value = match.get();
            if (repository.isWhitelisted(rule.getRuleId(), value.getSubject(), Instant.now(clock))) { continue; }
            matches.add(value);
            SecurityAlert alert = alertService.raise(value, event);
            alerts.add(alert);
        }
        return new PersistenceResult(matches, alerts);
    }

    private void reportInputIssues(SecurityEventDraft draft, EventInputValidation validation) {
        if (validation.getIssues().isEmpty()) {
            return;
        }
        try {
            inputIssueReporter.report(draft, validation);
        } catch (RuntimeException ignored) {
            // The event transaction has committed; input diagnostics are deliberately best effort.
        }
    }

    private static Set<String> enabledRuleIds(List<DetectionRule> rules) {
        Set<String> ruleIds = new LinkedHashSet<String>();
        for (DetectionRule rule : rules) {
            ruleIds.add(rule.getRuleId());
        }
        return Collections.unmodifiableSet(ruleIds);
    }

    /**
     * 保证当前内存事件以原始时间精度参与规则计算。
     *
     * <p>部分数据库会在持久化 {@code Instant} 时截断或舍入纳秒。若直接使用回读结果，当前事件可能在
     * 纳秒比较上晚于原始事件而被窗口规则误判为未来事件。按事件标识替换回读项可保持存储无关的
     * “先持久化、再以包含当前事件的历史评估”契约。</p>
     */
    private static List<SecurityEvent> canonicalHistory(SecurityEvent current, List<SecurityEvent> persisted) {
        List<SecurityEvent> history = new ArrayList<SecurityEvent>(persisted.size() + 1);
        boolean includedCurrent = false;
        for (SecurityEvent candidate : persisted) {
            if (current.getEventId().equals(candidate.getEventId())) {
                if (!includedCurrent) {
                    history.add(current);
                    includedCurrent = true;
                }
            } else {
                history.add(candidate);
            }
        }
        if (!includedCurrent) {
            history.add(current);
        }
        return history;
    }

    private void notifyAlerts(List<SecurityAlert> alerts) {
        for (SecurityAlert alert : alerts) {
            try {
                notificationChannel.notify(alert);
            } catch (RuntimeException ignored) {
                // The monitoring state has committed; notification delivery is deliberately best effort.
            }
        }
    }

    private List<ControlExecution> executeControls(List<RuleMatch> matches, List<SecurityAlert> alerts) {
        List<ControlExecution> controls = new ArrayList<ControlExecution>();
        if (mode != MonitoringMode.ENFORCE) {
            return controls;
        }
        for (int index = 0; index < matches.size(); index++) {
            RuleMatch match = matches.get(index);
            SecurityAlert alert = alerts.get(index);
            for (ControlActionType action : match.getActions()) {
                if (action == ControlActionType.RECORD) { continue; }
                ControlCommand command = new ControlCommand(alert.getAlertId() + ":" + action, alert.getAlertId(),
                    match.getSubject(), action, Instant.now(clock).plus(match.getControlTtl()), match.getRuleId());
                controls.add(controlService.execute(command));
            }
        }
        return controls;
    }

    /** @return 当前监测器使用的控制服务，可供宿主进行显式控制集成 */
    public DefaultControlService getControlService() { return controlService; }

    private static final class PersistenceResult {
        private final List<RuleMatch> matches;
        private final List<SecurityAlert> alerts;

        private PersistenceResult(List<RuleMatch> matches, List<SecurityAlert> alerts) {
            this.matches = matches;
            this.alerts = alerts;
        }
    }
}
