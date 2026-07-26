package io.github.jasper.monitoring.api.fact;

import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Mutable-at-startup, immutable-at-runtime catalog of fact validation and persistence metadata. */
public final class FactCatalog {
    private final Map<Class<? extends FactType<?>>, FactDefinition<?>> byType =
        new LinkedHashMap<Class<? extends FactType<?>>, FactDefinition<?>>();
    private final Map<String, FactDefinition<?>> byKey =
        new LinkedHashMap<String, FactDefinition<?>>();
    private boolean frozen;

    public void register(FactDefinition<?> definition) {
        requireMutable();
        Objects.requireNonNull(definition, "definition");
        if (byType.containsKey(definition.getFactType())) {
            throw configuration("Fact type is already registered: " + definition.getFactType().getName());
        }
        if (byKey.containsKey(definition.getKey())) {
            throw configuration("Fact key is already registered: " + definition.getKey());
        }
        byType.put(definition.getFactType(), definition);
        byKey.put(definition.getKey(), definition);
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public FactDefinition<?> require(Class<? extends FactType<?>> factType) {
        FactDefinition<?> definition = byType.get(Objects.requireNonNull(factType, "factType"));
        if (definition == null) {
            throw configuration("Fact type is not registered: " + factType.getName());
        }
        return definition;
    }

    public Map<Class<? extends FactType<?>>, FactDefinition<?>> asMap() {
        return Collections.unmodifiableMap(
            new LinkedHashMap<Class<? extends FactType<?>>, FactDefinition<?>>(byType));
    }

    private void requireMutable() {
        if (frozen) {
            throw configuration("Fact catalog is frozen");
        }
    }

    private static MonitoringConfigurationException configuration(String message) {
        return new MonitoringConfigurationException(MonitoringErrorCode.CONFLICTING_ACTION_DEFINITION, message);
    }
}
