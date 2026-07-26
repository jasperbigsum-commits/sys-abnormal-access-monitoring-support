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

/** Runtime-owned action resolution and fact collection boundary. */
public interface MonitoringRuntimePort {
    ActionDefinition resolve(Class<? extends ActionType> actionType);
    FactCollection collect(ActionExecution execution, ActionDefinition action);

    /** Immutable facts together with the trust source of each individual fact. */
    final class FactCollection {
        private final ActionFacts facts;
        private final Map<Class<? extends FactType<?>>, FactSource> sources;
        private final java.util.List<EventFact> persistedFacts;

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

        public ActionFacts getFacts() { return facts; }
        public Map<Class<? extends FactType<?>>, FactSource> getSources() { return sources; }
        public java.util.List<EventFact> getPersistedFacts() { return persistedFacts; }

        public static FactCollection empty() {
            return new FactCollection(ActionFacts.builder().build(),
                Collections.<Class<? extends FactType<?>>, FactSource>emptyMap(),
                Collections.<EventFact>emptyList());
        }
    }
}
