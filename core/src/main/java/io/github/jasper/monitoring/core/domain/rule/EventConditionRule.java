package io.github.jasper.monitoring.core.domain.rule;



import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.RuleMatch;
import io.github.jasper.monitoring.api.rule.RuleDefinition;
import io.github.jasper.monitoring.api.rule.RuleType;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 可仅根据当前事件判断的通用条件规则。
 *
 * <p>条件可以读取由宿主标准化后的事件属性，但不应依赖未经校验的客户端原始数据。</p>
 */
public final class EventConditionRule<R extends RuleType> extends AbstractDetectionRule<R> {
    private final Predicate<SecurityEvent> condition;

    /**
     * 创建当前事件条件规则。
     *
     * @param definition 规则的唯一静态定义
     * @param condition 对当前事件执行的判断条件
     * @param reason 面向操作人的命中说明
     */
    public EventConditionRule(RuleDefinition<R> definition, Predicate<SecurityEvent> condition, String reason) {
        super(definition, reason);
        this.condition = Objects.requireNonNull(condition, "condition");
    }

    @Override
    public Optional<RuleMatch> evaluate(RuleEvaluationContext context) {
        SecurityEvent event = context.getEvent();
        return condition.test(event) ? match(event) : Optional.<RuleMatch>empty();
    }
}
