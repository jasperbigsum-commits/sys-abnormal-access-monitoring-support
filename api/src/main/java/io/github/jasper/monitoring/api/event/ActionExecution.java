package io.github.jasper.monitoring.api.event;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only context for one monitored action execution.
 *
 * <p>The API intentionally exposes no mutators. Concrete execution data and
 * outcome ownership are introduced by the runtime assembly pipeline.</p>
 */
public interface ActionExecution {

    Class<? extends ActionType> getActionType();
    MonitoringRequestContext getRequestContext();
    IdentityContext getIdentityContext();
    ActionOutcome getOutcome();
    default ActionFacts getSuppliedFacts() { return ActionFacts.builder().build(); }
    default FactSource getSuppliedFactSource() { return FactSource.HOST_PROVIDER; }
    default Map<Class<? extends FactType<?>>, FactSource> getSuppliedFactSources() {
        Map<Class<? extends FactType<?>>, FactSource> result =
            new LinkedHashMap<Class<? extends FactType<?>>, FactSource>();
        for (Class<? extends FactType<?>> factType : getSuppliedFacts().asMap().keySet()) {
            result.put(factType, getSuppliedFactSource());
        }
        return Collections.unmodifiableMap(result);
    }

    static ActionExecution of(Class<? extends ActionType> actionType, MonitoringRequestContext request, IdentityContext identity,
                              ActionOutcome outcome) {
        return of(actionType, request, identity, outcome,
            ActionFacts.builder().build(), FactSource.HOST_PROVIDER);
    }

    static ActionExecution of(Class<? extends ActionType> actionType, MonitoringRequestContext request,
                              IdentityContext identity, ActionOutcome outcome, ActionFacts facts,
                              FactSource factSource) {
        Objects.requireNonNull(factSource, "factSource");
        Map<Class<? extends FactType<?>>, FactSource> sources =
            new LinkedHashMap<Class<? extends FactType<?>>, FactSource>();
        for (Class<? extends FactType<?>> factType : Objects.requireNonNull(facts, "facts").asMap().keySet()) {
            sources.put(factType, factSource);
        }
        return new ImmutableActionExecution(actionType, request, identity, outcome, facts, sources, factSource);
    }

    static ActionExecution of(Class<? extends ActionType> actionType, MonitoringRequestContext request,
                              IdentityContext identity, ActionOutcome outcome, ActionFacts facts,
                              Map<Class<? extends FactType<?>>, FactSource> factSources) {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(factSources, "factSources");
        if (!facts.asMap().keySet().equals(factSources.keySet())) {
            throw new IllegalArgumentException("Fact sources must exactly match supplied facts");
        }
        Map<Class<? extends FactType<?>>, FactSource> sources =
            new LinkedHashMap<Class<? extends FactType<?>>, FactSource>();
        for (Map.Entry<Class<? extends FactType<?>>, FactSource> entry : factSources.entrySet()) {
            sources.put(Objects.requireNonNull(entry.getKey(), "factSources contains null key"),
                Objects.requireNonNull(entry.getValue(), "factSources contains null source"));
        }
        return new ImmutableActionExecution(actionType, request, identity, outcome, facts, sources, null);
    }

    final class ImmutableActionExecution implements ActionExecution {
        private final MonitoringRequestContext request;
        private final IdentityContext identity;
        private final ActionOutcome outcome;
        private final Class<? extends ActionType> actionType;
        private final ActionFacts suppliedFacts;
        private final FactSource suppliedFactSource;
        private final Map<Class<? extends FactType<?>>, FactSource> suppliedFactSources;

        private ImmutableActionExecution(Class<? extends ActionType> actionType, MonitoringRequestContext request, IdentityContext identity,
                                         ActionOutcome outcome, ActionFacts suppliedFacts,
                                         Map<Class<? extends FactType<?>>, FactSource> suppliedFactSources,
                                         FactSource suppliedFactSource) {
            this.actionType = Objects.requireNonNull(actionType, "actionType");
            this.request = Objects.requireNonNull(request, "request");
            this.identity = Objects.requireNonNull(identity, "identity");
            this.outcome = Objects.requireNonNull(outcome, "outcome");
            this.suppliedFacts = Objects.requireNonNull(suppliedFacts, "suppliedFacts");
            this.suppliedFactSources = Collections.unmodifiableMap(
                new LinkedHashMap<Class<? extends FactType<?>>, FactSource>(suppliedFactSources));
            this.suppliedFactSource = suppliedFactSource == null
                ? uniformSourceOf(suppliedFactSources) : suppliedFactSource;
        }

        @Override public Class<? extends ActionType> getActionType() { return actionType; }
        @Override public MonitoringRequestContext getRequestContext() { return request; }
        @Override public IdentityContext getIdentityContext() { return identity; }
        @Override public ActionOutcome getOutcome() { return outcome; }
        @Override public ActionFacts getSuppliedFacts() { return suppliedFacts; }
        @Override public FactSource getSuppliedFactSource() {
            if (suppliedFactSource == null) {
                throw new IllegalStateException("Supplied facts use multiple sources");
            }
            return suppliedFactSource;
        }
        @Override public Map<Class<? extends FactType<?>>, FactSource> getSuppliedFactSources() {
            return suppliedFactSources;
        }

        private static FactSource uniformSourceOf(
                Map<Class<? extends FactType<?>>, FactSource> sources) {
            FactSource uniform = null;
            for (FactSource source : sources.values()) {
                if (uniform == null) uniform = source;
                else if (uniform != source) return null;
            }
            return uniform == null ? FactSource.HOST_PROVIDER : uniform;
        }
    }
}
