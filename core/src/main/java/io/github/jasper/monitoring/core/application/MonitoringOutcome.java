package io.github.jasper.monitoring.core.application;

import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.domain.ControlExecution;


import io.github.jasper.monitoring.core.domain.SecurityEvent;


import io.github.jasper.monitoring.core.domain.RuleMatch;
import io.github.jasper.monitoring.api.ControlActionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单条事件写入后的不可变结果，包含规则命中、告警及已尝试的控制动作。
 *
 * <p>列表仅反映本次 {@link SecurityMonitor#record(io.github.jasper.monitoring.api.SecurityEventDraft)}
 * 所产生的效果；未命中规则或处于观察模式时，相应列表可以为空。</p>
 */
public final class MonitoringOutcome {
    private final SecurityEvent event;
    private final List<RuleMatch> matches;
    private final List<SecurityAlert> alerts;
    private final List<ControlExecution> controls;
    /**
     * 创建一次监测处理结果。
     *
     * @param event 已由服务端加盖时间并完成持久化的事件
     * @param matches 本次事件命中的规则
     * @param alerts 由命中规则新建或刷新的告警
     * @param controls 执行模式下尝试执行的控制动作结果
     */
    public MonitoringOutcome(SecurityEvent event, List<RuleMatch> matches, List<SecurityAlert> alerts,
                             List<ControlExecution> controls) {
        this.event = event;
        this.matches = Collections.unmodifiableList(new ArrayList<RuleMatch>(matches));
        this.alerts = Collections.unmodifiableList(new ArrayList<SecurityAlert>(alerts));
        this.controls = Collections.unmodifiableList(new ArrayList<ControlExecution>(controls));
    }
    /** @return 本次已持久化的安全事件 */
    public SecurityEvent getEvent() { return event; }
    /** @return 本次命中的规则；返回只读列表 */
    public List<RuleMatch> getMatches() { return matches; }
    /** @return 本次新建或刷新的告警；返回只读列表 */
    public List<SecurityAlert> getAlerts() { return alerts; }
    /** @return 本次尝试执行的控制结果；观察模式下通常为空 */
    public List<ControlExecution> getControls() { return controls; }
    /**
     * 判断是否有命中规则建议执行指定控制动作，与当前是否真正处于执行模式无关。
     *
     * @param action 待判断的控制动作
     * @return 任一命中规则建议该动作时为 {@code true}
     */
    public boolean hasRisk(ControlActionType action) {
        for (RuleMatch match : matches) {
            if (match.getActions().contains(action)) { return true; }
        }
        return false;
    }
}
