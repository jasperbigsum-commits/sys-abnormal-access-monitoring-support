package io.github.jasper.monitoring.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 对一个事件草稿的规则输入质量结论。
 */
public final class EventInputValidation {
    private static final EventInputValidation VALID = new EventInputValidation(
        EventInputStatus.VALID, Collections.<EventInputIssue>emptyList(), Collections.<String>emptySet());

    private final EventInputStatus status;
    private final List<EventInputIssue> issues;
    private final Set<String> ineligibleRuleIds;

    private EventInputValidation(EventInputStatus status, Collection<EventInputIssue> issues,
                                 Collection<String> ineligibleRuleIds) {
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        this.status = status;
        this.issues = immutableIssues(issues);
        this.ineligibleRuleIds = immutableRuleIds(ineligibleRuleIds);
    }

    /**
     * @return 可供所有规则评估的有效结论
     */
    public static EventInputValidation valid() {
        return VALID;
    }

    /**
     * 创建缺少事实的结论，并列出不能评估的规则。
     *
     * @param issues 稳定且不含原始值的问题集合
     * @param ineligibleRuleIds 缺少所需事实的规则标识
     * @return 不完整的输入结论
     */
    public static EventInputValidation incomplete(Collection<EventInputIssue> issues,
                                                   Collection<String> ineligibleRuleIds) {
        return new EventInputValidation(EventInputStatus.INCOMPLETE, issues, ineligibleRuleIds);
    }

    /**
     * 创建指定状态的结论，供输入策略和持久化适配器重建历史结果使用。
     *
     * @param status 输入质量状态
     * @param issues 稳定且不含原始值的问题集合
     * @param ineligibleRuleIds 不可评估的规则标识
     * @return 不可变输入结论
     */
    public static EventInputValidation of(EventInputStatus status, Collection<EventInputIssue> issues,
                                          Collection<String> ineligibleRuleIds) {
        return new EventInputValidation(status, issues, ineligibleRuleIds);
    }

    /** @return 输入质量状态 */
    public EventInputStatus getStatus() {
        return status;
    }

    /** @return 不可变的稳定问题集合 */
    public List<EventInputIssue> getIssues() {
        return issues;
    }

    /** @return 不可变的不可评估规则标识集合 */
    public Set<String> getIneligibleRuleIds() {
        return ineligibleRuleIds;
    }

    /**
     * 判断规则是否可基于当前输入安全评估。
     *
     * <p>没有出现在不可评估集合中的自定义规则保持可用。</p>
     *
     * @param ruleId 待评估规则的标识
     * @return 规则标识有效且不在不可评估集合中时为 {@code true}
     */
    public boolean isEligible(String ruleId) {
        return ruleId != null && !ruleId.trim().isEmpty() && !ineligibleRuleIds.contains(ruleId.trim());
    }

    private static List<EventInputIssue> immutableIssues(Collection<EventInputIssue> values) {
        if (values == null) {
            throw new IllegalArgumentException("issues are required");
        }
        List<EventInputIssue> copied = new ArrayList<EventInputIssue>(values.size());
        for (EventInputIssue value : values) {
            if (value == null) {
                throw new IllegalArgumentException("issues must not contain null");
            }
            copied.add(value);
        }
        return Collections.unmodifiableList(copied);
    }

    private static Set<String> immutableRuleIds(Collection<String> values) {
        if (values == null) {
            throw new IllegalArgumentException("ineligibleRuleIds are required");
        }
        Set<String> copied = new LinkedHashSet<String>();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("ineligibleRuleIds must not contain blank values");
            }
            copied.add(value.trim());
        }
        return Collections.unmodifiableSet(copied);
    }
}
