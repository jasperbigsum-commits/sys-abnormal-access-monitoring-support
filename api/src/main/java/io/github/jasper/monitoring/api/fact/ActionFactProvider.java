package io.github.jasper.monitoring.api.fact;

import io.github.jasper.monitoring.api.event.ActionExecution;

/**
 * 动作事实提供器：基于一次只读执行上下文产出 Fact。
 * <p>
 * 注意：
 * Provider 只负责“如何计算值”，不负责“作用于哪些 Action”；适用范围由 {@link FactBinding} 显式声明。
 */
@FunctionalInterface
public interface ActionFactProvider {

    /**
     * 为当前动作执行生成事实集合。
     *
     * @param execution 当前动作执行上下文（只读）
     * @return 本次调用可观测到的事实，例如 baselineRatio、sensitivity、dataCount
     */
    ActionFacts provide(ActionExecution execution);
}
