package io.github.jasper.monitoring.core.domain;

import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;


import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.action.ActionDisposition;
import io.github.jasper.monitoring.api.action.ActionRequirement;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 确定性规则违反的不可变说明，以及建议的响应动作。
 *
 * <p>该对象描述“规则证据”，不直接执行控制动作；后续告警和控制执行由监测器编排。</p>
 */
public final class RuleMatch {
    private final String ruleId;
    private final RiskLevel riskLevel;
    private final String subject;
    private final String resourceKey;
    private final String reason;
    private final ActionDisposition disposition;
    private final Set<ActionRequirement> requirements;
    private final Set<ControlActionType> controls;
    private final Duration controlTtl;

    /**
     * 创建使用默认十五分钟控制有效期的命中结果。
     *
     * @param ruleId 命中规则的稳定标识
     * @param riskLevel 评估出的风险级别
     * @param subject 用于确定响应范围的用户、会话或 IP 主体
     * @param resourceKey 纳入告警去重的标准化资源范围
     * @param reason 面向操作人的命中说明
     * @param actions 建议的控制动作
     */
    public RuleMatch(String ruleId, RiskLevel riskLevel, String subject, String resourceKey, String reason,
                     ActionDisposition disposition, Set<ActionRequirement> requirements,
                     Set<ControlActionType> controls, Duration controlTtl) {
        this.ruleId = ruleId;
        this.riskLevel = riskLevel;
        this.subject = subject;
        this.resourceKey = resourceKey == null ? "" : resourceKey;
        this.reason = reason;
        this.disposition = disposition;
        this.requirements = requirements.isEmpty() ? Collections.<ActionRequirement>emptySet()
            : Collections.unmodifiableSet(EnumSet.copyOf(requirements));
        this.controls = controls.isEmpty() ? Collections.<ControlActionType>emptySet()
            : Collections.unmodifiableSet(EnumSet.copyOf(controls));
        if (controlTtl == null || controlTtl.isNegative() || controlTtl.isZero()) {
            throw new MonitoringValidationException(MonitoringErrorCode.INVALID_FIELD_VALUE,
                "controlTtl must be positive");
        }
        this.controlTtl = controlTtl;
    }
    /** @return 命中规则的稳定标识 */
    public String getRuleId() { return ruleId; }
    /** @return 本次命中的风险级别 */
    public RiskLevel getRiskLevel() { return riskLevel; }
    /** @return 控制动作应关联的用户、会话或 IP 主体 */
    public String getSubject() { return subject; }
    /** @return 用于告警去重的标准化资源范围 */
    public String getResourceKey() { return resourceKey; }
    /** @return 面向操作人的命中说明 */
    public String getReason() { return reason; }
    /** @return 当前动作是否可以继续 */
    public ActionDisposition getDisposition() { return disposition; }
    /** @return 新尝试重新获得放行资格前必须满足的要求 */
    public Set<ActionRequirement> getRequirements() { return requirements; }
    /** @return 对宿主未来状态产生影响的独立控制 */
    public Set<ControlActionType> getControls() { return controls; }
    /** @return 临时控制动作的有效期 */
    public Duration getControlTtl() { return controlTtl; }
    /** @return 由规则、主体与资源范围组成的稳定告警去重键 */
    public String fingerprint() { return ruleId + "|" + subject + "|" + resourceKey; }
}
