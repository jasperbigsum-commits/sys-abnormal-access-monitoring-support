package io.github.jasper.monitoring.core.application;

import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.core.domain.EventFact;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 运行时持有的动作解析与事实采集边界。 */
public interface MonitoringRuntimePort {
    /**
     * 解析动作类型对应的静态动作定义。
     *
     * @param actionType 动作类型标识
     * @return 动作定义
     */
    ActionDefinition resolve(Class<? extends ActionType> actionType);

    /**
     * 执行动作事实采集并返回可持久化快照。
     *
     * @param execution 动作执行上下文
     * @param action 动作定义
     * @return 事实集合（含来源与持久化快照）
     */
    FactCollection collect(ActionExecution execution, ActionDefinition action);

    /** 不可变事实集合及每条事实的可信来源。 */
    final class FactCollection {
        private final ActionFacts facts;
        private final Map<Class<? extends FactType<?>>, FactSource> sources;
        private final java.util.List<EventFact> persistedFacts;

        /**
         * 创建事实采集结果。
         *
         * @param facts 运行时事实集合
         * @param sources 事实类型到来源的映射
         * @param persistedFacts 与事实一一对应的持久化快照
         */
        public FactCollection(ActionFacts facts,
                Map<Class<? extends FactType<?>>, FactSource> sources,
                java.util.List<EventFact> persistedFacts) {
            this.facts = Objects.requireNonNull(facts, "facts");
            Map<Class<? extends FactType<?>>, FactSource> copy =
                new LinkedHashMap<Class<? extends FactType<?>>, FactSource>(
                    Objects.requireNonNull(sources, "sources"));
            if (!copy.keySet().equals(facts.asMap().keySet()) || copy.containsValue(null)) {
                throw new IllegalArgumentException("Every collected fact must have exactly one source");
            }
            this.sources = Collections.unmodifiableMap(copy);
            this.persistedFacts = Collections.unmodifiableList(
                new java.util.ArrayList<EventFact>(Objects.requireNonNull(persistedFacts, "persistedFacts")));
            if (this.persistedFacts.size() != facts.asMap().size()) {
                throw new IllegalArgumentException("Every collected fact must have one persistence snapshot");
            }
        }

        /** @return 运行时事实集合 */
        public ActionFacts getFacts() { return facts; }
        /** @return 事实类型到来源的只读映射 */
        public Map<Class<? extends FactType<?>>, FactSource> getSources() { return sources; }
        /** @return 与事实集合一一对应的持久化快照列表 */
        public java.util.List<EventFact> getPersistedFacts() { return persistedFacts; }

        /** @return 空事实集合 */
        public static FactCollection empty() {
            return new FactCollection(ActionFacts.builder().build(),
                Collections.<Class<? extends FactType<?>>, FactSource>emptyMap(),
                Collections.<EventFact>emptyList());
        }
    }
}
