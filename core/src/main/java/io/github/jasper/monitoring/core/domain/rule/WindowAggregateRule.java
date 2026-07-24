package io.github.jasper.monitoring.core.domain.rule;


import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.RuleMatch;


import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.RiskLevel;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 在包含边界的滚动窗口内汇总匹配事件并与阈值比较的通用规则。
 *
 * <p>触发条件和候选条件使宿主可以使用事件类型或标准化属性定义规则，而无需为每种业务规则
 * 新建专用实现类。</p>
 */
public final class WindowAggregateRule extends AbstractDetectionRule {
    /** 基线规则支持的聚合方式。 */
    public enum Aggregation {
        /** 统计匹配事件数量。 */
        EVENT_COUNT,
        /** 统计匹配事件中非空资源标识的去重数量。 */
        DISTINCT_RESOURCE_COUNT,
        /** 累加事件数据量；零值按一个观测项计算。 */
        DATA_COUNT
    }

    /** 聚合候选事件前使用的主体关联范围。 */
    public enum Scope {
        /** 按有效用户或匿名 IP 主体关联事件。 */
        USER,
        /** 触发事件存在会话时按会话关联，否则按主体关联。 */
        SESSION_OR_USER
    }

    private final Predicate<SecurityEvent> trigger;
    private final Predicate<SecurityEvent> candidate;
    private final Duration window;
    private final long threshold;
    private final Scope scope;
    private final Aggregation aggregation;

    /**
     * 创建滚动窗口聚合规则。
     *
     * @param ruleId 用于告警和控制关联的稳定标识
     * @param trigger 决定当前事件是否开始评估的条件
     * @param candidate 应用于纳入聚合的历史事件条件
     * @param window 包含起止边界的回溯窗口
     * @param threshold 达到该正数阈值时规则命中
     * @param scope 当前事件与候选事件的关联范围
     * @param aggregation 对匹配候选事件使用的计算方式
     * @param riskLevel 达到阈值时输出的风险级别
     * @param actions 命中时建议执行的控制动作
     * @param reason 面向操作人的命中说明
     */
    public WindowAggregateRule(String ruleId, Predicate<SecurityEvent> trigger, Predicate<SecurityEvent> candidate,
                               Duration window, long threshold, Scope scope, Aggregation aggregation,
                               RiskLevel riskLevel, List<ControlActionType> actions, String reason) {
        super(ruleId, riskLevel, actions, reason);
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.candidate = Objects.requireNonNull(candidate, "candidate");
        this.window = Objects.requireNonNull(window, "window");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }
        if (threshold <= 0) {
            throw new IllegalArgumentException("threshold must be positive");
        }
        this.threshold = threshold;
        this.scope = Objects.requireNonNull(scope, "scope");
        this.aggregation = Objects.requireNonNull(aggregation, "aggregation");
    }

    @Override
    public Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history) {
        if (!trigger.test(event)) {
            return Optional.empty();
        }
        return aggregate(event, history) >= threshold ? match(event) : Optional.<RuleMatch>empty();
    }

    private long aggregate(SecurityEvent event, List<SecurityEvent> history) {
        if (aggregation == Aggregation.DISTINCT_RESOURCE_COUNT) {
            Set<String> resources = new HashSet<String>();
            for (SecurityEvent candidateEvent : history) {
                if (included(event, candidateEvent) && candidateEvent.getResourceId() != null) {
                    resources.add(candidateEvent.getResourceId());
                }
            }
            return resources.size();
        }

        long total = 0;
        for (SecurityEvent candidateEvent : history) {
            if (included(event, candidateEvent)) {
                if (aggregation == Aggregation.DATA_COUNT && !candidateEvent.hasDataCount()) {
                    continue;
                }
                long value = aggregation == Aggregation.DATA_COUNT ? Math.max(1, candidateEvent.getDataCount()) : 1;
                total = addSaturated(total, value);
            }
        }
        return total;
    }

    private static long addSaturated(long total, long value) {
        return Long.MAX_VALUE - total < value ? Long.MAX_VALUE : total + value;
    }

    private boolean included(SecurityEvent event, SecurityEvent candidateEvent) {
        Instant start = event.getOccurredAt().minus(window);
        return !candidateEvent.getOccurredAt().isBefore(start)
            && !candidateEvent.getOccurredAt().isAfter(event.getOccurredAt())
            && sameScope(event, candidateEvent)
            && candidate.test(candidateEvent);
    }

    private boolean sameScope(SecurityEvent event, SecurityEvent candidateEvent) {
        if (scope == Scope.USER) {
            return event.subject().equals(candidateEvent.subject());
        }
        String sessionId = event.getSessionIdHash();
        return sessionId != null ? sessionId.equals(candidateEvent.getSessionIdHash())
            : event.subject().equals(candidateEvent.subject());
    }
}
