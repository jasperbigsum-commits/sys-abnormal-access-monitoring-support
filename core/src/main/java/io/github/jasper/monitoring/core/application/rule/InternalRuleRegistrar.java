package io.github.jasper.monitoring.core.application.rule;


import io.github.jasper.monitoring.core.domain.rule.DetectionRule;

/**
 * 启动期内部规则注册接口。
 *
 * <p>该接口只用于把代码实现的 {@link DetectionRule} 注册到当前 JVM 的规则快照。注册完成并冻结后，
 * 运行中的管理操作不能修改这些规则；需要动态管理的规则定义应走持久化管理通道，而不是改写此注册器。</p>
 */
public interface InternalRuleRegistrar {
    /**
     * 注册一条确定性、无副作用的内部规则。
     *
     * @param rule 由宿主代码实现的规则；规则 ID 在当前进程内必须唯一
     */
    void register(DetectionRule rule);
}
