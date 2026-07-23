package io.github.jasper.monitoring.core.domain.rule;

import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.application.DefaultSecurityMonitor;
import io.github.jasper.monitoring.core.domain.RuleMatch;


import java.util.List;
import java.util.Optional;

/**
 * 针对当前事件及其时间窗口历史执行的确定性、无副作用规则策略。
 *
 * <p>{@link DefaultSecurityMonitor} 会先持久化当前事件，再提供包含该事件的时间顺序历史。
 * 规则实现只返回命中证据；告警持久化、通知和宿主控制动作均由规则之外的组件负责。</p>
 */
public interface DetectionRule {
    /** @return 用于告警、控制和白名单的稳定规则标识 */
    String getRuleId();

    /**
     * 使用提供的有限历史评估一条事件。
     *
     * @param event 当前正在记录的事件
     * @param history 可供规则使用的时间顺序事件历史；由 {@link DefaultSecurityMonitor} 调用时包含 {@code event}
     * @return 含风险级别和建议动作的命中结果；未违反规则时返回空值
     */
    Optional<RuleMatch> evaluate(SecurityEvent event, List<SecurityEvent> history);
}
