package io.github.jasper.monitoring.api;

/**
 * 规则管理查询的最小公共视图。
 *
 * <p>管理端可按来源和可变性区分代码发布的内部规则与数据库管理的规则配置，避免错误地把内部基线当作
 * 可在线编辑的配置。</p>
 */
public interface RuleManagementEntry {
    /** @return 稳定规则标识，供告警、白名单和控制审计关联 */
    String getRuleId();

    /** @return 当前条目的规则来源 */
    RuleSource getSource();

    /**
     * @return 管理端是否可以修改条目状态或创建新版本；内部注册规则始终返回 {@code false}
     */
    boolean isMutable();
}
