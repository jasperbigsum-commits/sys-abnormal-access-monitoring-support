package io.github.jasper.monitoring.core.application.rule;


import io.github.jasper.monitoring.api.RuleManagementEntry;
import io.github.jasper.monitoring.api.RuleSource;

/** 内部启动期规则在管理查询中的不可变视图。 */
public final class InternalRuleEntry implements RuleManagementEntry {
    private final String ruleId;

    /**
     * @param ruleId 已注册的稳定规则标识
     */
    public InternalRuleEntry(String ruleId) {
        this.ruleId = ruleId;
    }

    /** @return 已注册的稳定规则标识 */
    @Override
    public String getRuleId() {
        return ruleId;
    }

    /** @return {@link RuleSource#INTERNAL} */
    @Override
    public RuleSource getSource() {
        return RuleSource.INTERNAL;
    }

    /** @return {@code false}，内部规则必须通过代码发布变更 */
    @Override
    public boolean isMutable() {
        return false;
    }
}
