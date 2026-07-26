package io.github.jasper.monitoring.api.rule;

import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Mutable-at-startup, frozen-at-runtime catalog keyed only by rule type tokens. */
public final class RuleCatalog {
    private final Map<Class<? extends RuleType>, RuleDefinition<?>> definitions =
        new LinkedHashMap<Class<? extends RuleType>, RuleDefinition<?>>();
    private final Map<String, Class<? extends RuleType>> typesById =
        new LinkedHashMap<String, Class<? extends RuleType>>();
    private boolean frozen;

    public synchronized <R extends RuleType> void register(RuleDefinition<R> definition) {
        requireMutable();
        Objects.requireNonNull(definition, "definition");
        Class<R> type = definition.getType();
        int modifiers = type.getModifiers();
        if (type.isInterface() || Modifier.isAbstract(modifiers) || !Modifier.isFinal(modifiers)) {
            throw conflict("Rule type must be concrete and final: " + type.getName());
        }
        if (definitions.containsKey(type)) {
            throw conflict("Rule type is already registered: " + type.getName());
        }
        if (typesById.containsKey(definition.getId())) {
            throw conflict("Rule id is already registered: " + definition.getId());
        }
        definitions.put(type, definition);
        typesById.put(definition.getId(), type);
    }

    public synchronized void freeze() {
        frozen = true;
    }

    public synchronized boolean isFrozen() {
        return frozen;
    }

    @SuppressWarnings("unchecked")
    public synchronized <R extends RuleType> RuleDefinition<R> require(Class<R> type) {
        Objects.requireNonNull(type, "type");
        RuleDefinition<?> definition = definitions.get(type);
        if (definition == null) {
            throw conflict("Rule type is not registered: " + type.getName());
        }
        return (RuleDefinition<R>) definition;
    }

    public synchronized Map<Class<? extends RuleType>, RuleDefinition<?>> asMap() {
        return Collections.unmodifiableMap(
            new LinkedHashMap<Class<? extends RuleType>, RuleDefinition<?>>(definitions));
    }

    private void requireMutable() {
        if (frozen) {
            throw new MonitoringConfigurationException(MonitoringErrorCode.RULE_REGISTRY_FROZEN,
                "Rule catalog is frozen");
        }
    }

    private static MonitoringConfigurationException conflict(String message) {
        return new MonitoringConfigurationException(
            MonitoringErrorCode.DUPLICATE_INTERNAL_RULE_ID, message);
    }
}
