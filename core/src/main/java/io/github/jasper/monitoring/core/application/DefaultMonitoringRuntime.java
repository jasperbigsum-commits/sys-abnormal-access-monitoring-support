package io.github.jasper.monitoring.core.application;

import io.github.jasper.monitoring.api.action.ActionCatalog;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactBinding;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactCatalog;
import io.github.jasper.monitoring.api.fact.FactDefinition;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import io.github.jasper.monitoring.core.domain.EventFact;

/** Default runtime that resolves frozen action metadata and invokes explicitly bound fact providers. */
public final class DefaultMonitoringRuntime implements MonitoringRuntimePort {
    private final ActionCatalog catalog;
    private final FactCatalog factCatalog;
    private final List<FactBinding> bindings;

    public DefaultMonitoringRuntime(ActionCatalog catalog, FactCatalog factCatalog, List<FactBinding> bindings) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        if (!catalog.isFrozen()) {
            throw new IllegalArgumentException("Action catalog must be frozen before runtime creation");
        }
        this.factCatalog = Objects.requireNonNull(factCatalog, "factCatalog");
        if (!factCatalog.isFrozen()) {
            throw new IllegalArgumentException("Fact catalog must be frozen before runtime creation");
        }
        validateActionFacts(catalog, factCatalog);
        List<FactBinding> copy = new ArrayList<FactBinding>(
            Objects.requireNonNull(bindings, "bindings"));
        validateBindings(catalog, factCatalog, copy);
        this.bindings = Collections.unmodifiableList(copy);
    }

    @Override
    public ActionDefinition resolve(Class<? extends ActionType> actionType) {
        return catalog.require(actionType);
    }

    @Override
    public FactCollection collect(ActionExecution execution, ActionDefinition action) {
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(action, "action");
        Map<Class<? extends FactType<?>>, Object> values =
            new LinkedHashMap<Class<? extends FactType<?>>, Object>();
        Map<Class<? extends FactType<?>>, FactSource> sources =
            new LinkedHashMap<Class<? extends FactType<?>>, FactSource>();
        addSupplied(values, sources, execution, action);
        for (FactBinding binding : bindings) {
            if (!binding.appliesTo(execution.getActionType())) {
                continue;
            }
            ActionFacts contribution = Objects.requireNonNull(binding.getProvider().provide(execution),
                "Fact provider returned null");
            add(values, sources, contribution, action, binding.getSource(), binding);
        }
        return new FactCollection(facts(values), sources, snapshots(values, sources));
    }

    private static void addSupplied(Map<Class<? extends FactType<?>>, Object> values,
            Map<Class<? extends FactType<?>>, FactSource> sources, ActionExecution execution,
            ActionDefinition action) {
        ActionFacts suppliedFacts = execution.getSuppliedFacts();
        Map<Class<? extends FactType<?>>, FactSource> suppliedSources = execution.getSuppliedFactSources();
        if (!suppliedFacts.asMap().keySet().equals(suppliedSources.keySet())
                || suppliedSources.containsValue(null)) {
            throw new IllegalStateException("Every supplied fact must have exactly one source");
        }
        for (Map.Entry<Class<? extends FactType<?>>, Object> entry : suppliedFacts.asMap().entrySet()) {
            addEntry(values, sources, entry, action, suppliedSources.get(entry.getKey()), null);
        }
    }

    private static void add(Map<Class<? extends FactType<?>>, Object> values,
            Map<Class<? extends FactType<?>>, FactSource> sources, ActionFacts contribution,
            ActionDefinition action, FactSource source, FactBinding binding) {
        for (Map.Entry<Class<? extends FactType<?>>, Object> entry : contribution.asMap().entrySet()) {
            addEntry(values, sources, entry, action, source, binding);
        }
    }

    private static void addEntry(Map<Class<? extends FactType<?>>, Object> values,
            Map<Class<? extends FactType<?>>, FactSource> sources,
            Map.Entry<Class<? extends FactType<?>>, Object> entry, ActionDefinition action,
            FactSource source, FactBinding binding) {
        Class<? extends FactType<?>> factType = entry.getKey();
        if (binding != null && !binding.getDeclaredFacts().contains(factType)) {
            throw new IllegalStateException("Fact provider returned an undeclared fact: " + factType.getName());
        }
        if (!action.getRequiredFacts().contains(factType) && !action.getOptionalFacts().contains(factType)) {
            throw new IllegalStateException("Fact is not declared by action: " + factType.getName());
        }
        if (!action.getAllowedSources(factType).contains(source)) {
            throw new IllegalStateException("Fact source is not approved by action: " + factType.getName());
        }
        if (values.put(factType, entry.getValue()) != null) {
            throw new IllegalStateException("Multiple sources returned the same fact: " + factType.getName());
        }
        sources.put(factType, source);
    }

    private static void validateBindings(ActionCatalog catalog, FactCatalog factCatalog,
            List<FactBinding> bindings) {
        Map<Class<? extends ActionType>, Map<Class<? extends FactType<?>>, FactBinding>> ownership =
            new LinkedHashMap<Class<? extends ActionType>, Map<Class<? extends FactType<?>>, FactBinding>>();
        for (FactBinding binding : bindings) {
            Objects.requireNonNull(binding, "bindings contains null");
            boolean matched = false;
            for (Map.Entry<Class<? extends ActionType>, ActionDefinition> entry : catalog.asMap().entrySet()) {
                if (!binding.appliesTo(entry.getKey())) {
                    continue;
                }
                matched = true;
                validateBinding(entry.getKey(), entry.getValue(), factCatalog, binding, ownership);
            }
            if (!matched) {
                throw configuration("Fact binding does not match a registered action");
            }
        }
    }

    private static void validateBinding(Class<? extends ActionType> actionType, ActionDefinition action,
            FactCatalog factCatalog, FactBinding binding,
            Map<Class<? extends ActionType>, Map<Class<? extends FactType<?>>, FactBinding>> ownership) {
        Map<Class<? extends FactType<?>>, FactBinding> actionOwnership = ownership.get(actionType);
        if (actionOwnership == null) {
            actionOwnership = new LinkedHashMap<Class<? extends FactType<?>>, FactBinding>();
            ownership.put(actionType, actionOwnership);
        }
        for (Class<? extends FactType<?>> factType : binding.getDeclaredFacts()) {
            FactDefinition<?> definition = factCatalog.require(factType);
            if (!action.getRequiredFacts().contains(factType) && !action.getOptionalFacts().contains(factType)) {
                throw configuration("Fact binding declares a fact not owned by action " + actionType.getName()
                    + ": " + factType.getName());
            }
            if (!action.getAllowedSources(factType).contains(binding.getSource())) {
                throw configuration("Fact binding source is not approved by action " + actionType.getName()
                    + ": " + factType.getName());
            }
            if (!definition.allows(binding.getSource())) {
                throw configuration("Fact binding source is not approved by fact definition "
                    + definition.getKey());
            }
            if (actionOwnership.put(factType, binding) != null) {
                throw configuration("Multiple bindings own fact " + factType.getName()
                    + " for action " + actionType.getName());
            }
        }
    }

    private static void validateActionFacts(ActionCatalog actions, FactCatalog facts) {
        for (Map.Entry<Class<? extends ActionType>, ActionDefinition> entry : actions.asMap().entrySet()) {
            java.util.Set<Class<? extends FactType<?>>> declared =
                new java.util.LinkedHashSet<Class<? extends FactType<?>>>(entry.getValue().getRequiredFacts());
            declared.addAll(entry.getValue().getOptionalFacts());
            for (Class<? extends FactType<?>> factType : declared) {
                FactDefinition<?> definition = facts.require(factType);
                if (java.util.Collections.disjoint(entry.getValue().getAllowedSources(factType),
                        definition.getAllowedSources())) {
                    throw configuration("Action and fact definition have no common source: "
                        + entry.getKey().getName() + " / " + definition.getKey());
                }
            }
        }
    }

    private List<EventFact> snapshots(Map<Class<? extends FactType<?>>, Object> values,
            Map<Class<? extends FactType<?>>, FactSource> sources) {
        List<EventFact> result = new ArrayList<EventFact>(values.size());
        for (Map.Entry<Class<? extends FactType<?>>, Object> entry : values.entrySet()) {
            FactDefinition<?> definition = factCatalog.require(entry.getKey());
            FactSource source = sources.get(entry.getKey());
            if (!definition.allows(source)) {
                throw new IllegalStateException("Fact source is not approved by definition: "
                    + definition.getKey());
            }
            result.add(new EventFact(definition.getKey(), definition.getValueType().getName(),
                definition.encodeRaw(entry.getValue()), source));
        }
        return result;
    }

    private static MonitoringConfigurationException configuration(String message) {
        return new MonitoringConfigurationException(
            MonitoringErrorCode.CONFLICTING_ACTION_DEFINITION, message);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ActionFacts facts(Map<Class<? extends FactType<?>>, Object> values) {
        ActionFacts.Builder builder = ActionFacts.builder();
        for (Map.Entry<Class<? extends FactType<?>>, Object> entry : values.entrySet()) {
            builder.put((Class) entry.getKey(), entry.getValue());
        }
        return builder.build();
    }
}
