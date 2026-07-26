package io.github.jasper.monitoring.core.domain.rule;

import io.github.jasper.monitoring.core.domain.RuleMatch;
import io.github.jasper.monitoring.api.rule.RuleDefinition;
import io.github.jasper.monitoring.api.rule.RuleType;
import java.util.Optional;

/**
 * 针对当前事件及其时间窗口历史执行的确定性、无副作用规则策略。
 *
 * <p>类型化规则评估服务会先持久化当前事件，再提供包含该事件的时间顺序历史。
 * 规则实现只返回命中证据；告警持久化、通知和宿主控制动作均由规则之外的组件负责。</p>
 */
public interface DetectionRule<R extends RuleType> {
    /** @return the complete static definition for this rule */
    RuleDefinition<R> definition();

    /** @return the statically registered rule token */
    default Class<R> type() {
        return definition().getType();
    }

    /** Returns the stable identifier owned by the typed definition. */
    default String getRuleId() {
        return definition().getId();
    }

    /** Evaluates through the typed context without consulting an external policy. */
    Optional<RuleMatch> evaluate(RuleEvaluationContext context);

}
