package io.github.jasper.monitoring.core.domain;


import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.RiskLevel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private final List<ControlActionType> actions;
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
                     List<ControlActionType> actions) {
        this(ruleId, riskLevel, subject, resourceKey, reason, actions, Duration.ofMinutes(15));
    }

    /**
     * 创建具有显式正数有效期的命中结果，用于临时控制动作。
     *
     * @param ruleId 命中规则的稳定标识
     * @param riskLevel 评估出的风险级别
     * @param subject 用于确定响应范围的用户、会话或 IP 主体
     * @param resourceKey 纳入告警去重的标准化资源范围
     * @param reason 面向操作人的命中说明
     * @param actions 建议的控制动作
     * @param controlTtl 本次命中请求宿主控制动作的有效期
     * @throws IllegalArgumentException {@code controlTtl} 为零、负数或 {@code null} 时
     */
    public RuleMatch(String ruleId, RiskLevel riskLevel, String subject, String resourceKey, String reason,
                     List<ControlActionType> actions, Duration controlTtl) {
        this.ruleId = ruleId;
        this.riskLevel = riskLevel;
        this.subject = subject;
        this.resourceKey = resourceKey == null ? "" : resourceKey;
        this.reason = reason;
        this.actions = Collections.unmodifiableList(new ArrayList<ControlActionType>(actions));
        if (controlTtl == null || controlTtl.isNegative() || controlTtl.isZero()) {
            throw new IllegalArgumentException("controlTtl must be positive");
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
    /** @return 建议的控制动作；返回只读列表 */
    public List<ControlActionType> getActions() { return actions; }
    /** @return 临时控制动作的有效期 */
    public Duration getControlTtl() { return controlTtl; }
    /** @return 由规则、主体与资源范围组成的稳定告警去重键 */
    public String fingerprint() { return ruleId + "|" + subject + "|" + resourceKey; }
}
