package io.github.jasper.monitoring.core.domain.rule;



import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.RuleMatch;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.RiskLevel;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 可仅根据当前事件判断的通用条件规则。
 *
 * <p>条件可以读取由宿主标准化后的事件属性，但不应依赖未经校验的客户端原始数据。</p>
 */
public final class EventConditionRule extends AbstractDetectionRule {
    private final Predicate<SecurityEvent> condition;

    /**
     * 创建当前事件条件规则。
     *
     * @param ruleId 用于告警和控制关联的稳定标识
     * @param condition 对当前事件执行的判断条件
     * @param riskLevel 条件成立时输出的风险级别
     * @param actions 命中时建议执行的控制动作
     * @param reason 面向操作人的命中说明
     */
    public EventConditionRule(String ruleId, Predicate<SecurityEvent> condition, RiskLevel riskLevel,
                              List<ControlActionType> actions, String reason) {
        super(ruleId, riskLevel, actions, reason);
        this.condition = Objects.requireNonNull(condition, "condition");
    }

    @Override
    public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
        return condition.test(event) ? match(event) : Optional.<RuleMatch>empty();
    }
}
