package io.github.jasper.monitoring.api;

/**
 * 规则在管理视图中的来源。
 *
 * <p>来源决定可变性边界：内部规则随应用发布，在运行进程中冻结；持久化规则由管理侧版本化、查询和启停，
 * 是否进入运行时仍取决于经过审批的规则加载器。</p>
 */
public enum RuleSource {
    /** 由代码和启动期注册器提供，运行中不可修改。 */
    INTERNAL,
    /** 由管理数据库保存，可进行版本化与启停管理。 */
    PERSISTED
}
