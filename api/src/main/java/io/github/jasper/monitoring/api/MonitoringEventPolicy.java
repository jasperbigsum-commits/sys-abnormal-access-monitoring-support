package io.github.jasper.monitoring.api;

import java.util.Set;

/**
 * 评估事件草稿能否为已启用的监测规则提供所需事实的框架无关策略。
 */
@FunctionalInterface
public interface MonitoringEventPolicy {
    /**
     * 验证一个事件草稿，并标识因输入质量无法安全评估的规则。
     *
     * @param draft 已清洗的宿主事件草稿
     * @param enabledRuleIds 当前启用的规则标识
     * @return 不可变输入质量结论
     */
    EventInputValidation validate(SecurityEventDraft draft, Set<String> enabledRuleIds);
}
