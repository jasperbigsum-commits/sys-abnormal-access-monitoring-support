package io.github.jasper.monitoring.core.application.rule;



import io.github.jasper.monitoring.core.application.SecurityMonitor;

/**
 * 宿主用于声明内部不可变规则的扩展点。
 *
 * <p>Starter 在创建 {@link SecurityMonitor} 前调用所有贡献者，并在随后冻结注册表。适合代码发布随版本演进的
 * 合规基线、固定阈值或特定动作规则；不适合希望由管理端随时修改的规则配置。</p>
 */
@FunctionalInterface
public interface InternalRuleContributor {
    /**
     * 向启动期注册器贡献规则。
     *
     * @param registrar 只在监测器创建前可写的内部规则注册器
     */
    void register(InternalRuleRegistrar registrar);
}
