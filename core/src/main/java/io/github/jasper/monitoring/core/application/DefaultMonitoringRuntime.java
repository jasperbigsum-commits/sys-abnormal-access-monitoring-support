package io.github.jasper.monitoring.core.application;

import io.github.jasper.monitoring.api.action.ActionCatalog;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactBinding;
import io.github.jasper.monitoring.api.fact.FactType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Default runtime that resolves frozen action metadata and invokes explicitly bound fact providers. */
public final class DefaultMonitoringRuntime implements MonitoringRuntimePort {
    private final ActionCatalog catalog;
    private final List<FactBinding> bindings;

    public DefaultMonitoringRuntime(ActionCatalog catalog, List<FactBinding> bindings) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        if (!catalog.isFrozen()) {
            throw new IllegalArgumentException("Action catalog must be frozen before runtime creation");
        }
        this.bindings = Collections.unmodifiableList(
            new ArrayList<FactBinding>(Objects.requireNonNull(bindings, "bindings")));
    }

    @Override
    public ActionDefinition resolve(Class<? extends ActionType> actionType) {
        return catalog.require(actionType);
    }

    @Override
    public ActionFacts collect(ActionExecution execution, ActionDefinition action) {
        Objects.requireNonNull(execution, "execution");
        Objects.requireNonNull(action, "action");
        Map<Class<? extends FactType<?>>, Object> values =
            new LinkedHashMap<Class<? extends FactType<?>>, Object>();
        for (FactBinding binding : bindings) {
            if (!binding.appliesTo(execution.getActionType())) {
                continue;
            }
            ActionFacts contribution = Objects.requireNonNull(binding.getProvider().provide(execution),
                "Fact provider returned null");
            for (Map.Entry<Class<? extends FactType<?>>, Object> entry : contribution.asMap().entrySet()) {
                Class<? extends FactType<?>> factType = entry.getKey();
                if (!binding.getDeclaredFacts().contains(factType)) {
                    throw new IllegalStateException("Fact provider returned an undeclared fact: " + factType.getName());
                }
                if (!action.getRequiredFacts().contains(factType)
                    && !action.getOptionalFacts().contains(factType)) {
                    throw new IllegalStateException("Fact is not declared by action: " + factType.getName());
                }
                if (values.put(factType, entry.getValue()) != null) {
                    throw new IllegalStateException("Multiple providers returned the same fact: " + factType.getName());
                }
            }
        }
        return facts(values);
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
