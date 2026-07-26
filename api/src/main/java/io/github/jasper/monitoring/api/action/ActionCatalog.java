package io.github.jasper.monitoring.api.action;

import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Mutable-at-startup, immutable-at-runtime catalog of typed action definitions. */
public final class ActionCatalog {
    private final Map<Class<? extends ActionType>, ActionDefinition> definitions =
        new LinkedHashMap<Class<? extends ActionType>, ActionDefinition>();
    private final Map<String, Class<? extends ActionType>> typesByCode =
        new LinkedHashMap<String, Class<? extends ActionType>>();
    private final Map<Class<? extends ActionContract>, ActionContractDefinition> contracts =
        new LinkedHashMap<Class<? extends ActionContract>, ActionContractDefinition>();
    private boolean frozen;

    /** Registers one inheritable action contract before action definitions are registered. */
    public void registerContract(Class<? extends ActionContract> contractType,
            ActionContractDefinition definition) {
        requireMutable();
        Objects.requireNonNull(contractType, "contractType");
        Objects.requireNonNull(definition, "definition");
        if (!contractType.isInterface() || contractType == ActionContract.class
                || !ActionContract.class.isAssignableFrom(contractType)) {
            throw configuration("Action contract must be a specific interface: " + contractType.getName());
        }
        if (!definitions.isEmpty()) {
            throw configuration("Action contracts must be registered before action definitions");
        }
        if (contracts.containsKey(contractType)) {
            throw configuration("Action contract is already registered: " + contractType.getName());
        }
        contracts.put(contractType, definition);
    }

    /** Registers one concrete final action type and compiles its inherited contracts. */
    public void register(Class<? extends ActionType> actionType, ActionDefinition definition) {
        requireMutable();
        validateActionType(actionType);
        Objects.requireNonNull(definition, "definition");
        if (definitions.containsKey(actionType)) {
            throw configuration("Action type is already registered: " + actionType.getName());
        }
        if (typesByCode.containsKey(definition.getCode())) {
            throw configuration("Action code is already registered: " + definition.getCode());
        }

        ActionDefinition effective = definition;
        for (Class<? extends ActionContract> contractType : contractsOf(actionType)) {
            ActionContractDefinition contract = contracts.get(contractType);
            if (contract == null) {
                throw configuration("Action contract is not registered: " + contractType.getName());
            }
            effective = effective.merge(contract);
        }
        definitions.put(actionType, effective);
        typesByCode.put(effective.getCode(), actionType);
    }

    /** Prevents any further registration. Calling this method more than once is harmless. */
    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    /** Returns the registered definition for an exact action token. */
    public ActionDefinition require(Class<? extends ActionType> actionType) {
        Objects.requireNonNull(actionType, "actionType");
        ActionDefinition definition = definitions.get(actionType);
        if (definition == null) {
            throw new MonitoringConfigurationException(MonitoringErrorCode.ACTION_NOT_REGISTERED,
                "Action type is not registered: " + actionType.getName());
        }
        return definition;
    }

    /** @return an immutable point-in-time view keyed only by typed action tokens */
    public Map<Class<? extends ActionType>, ActionDefinition> asMap() {
        return Collections.unmodifiableMap(
            new LinkedHashMap<Class<? extends ActionType>, ActionDefinition>(definitions));
    }

    private void requireMutable() {
        if (frozen) {
            throw new MonitoringConfigurationException(MonitoringErrorCode.ACTION_CATALOG_FROZEN,
                "Action catalog is frozen");
        }
    }

    private static void validateActionType(Class<? extends ActionType> actionType) {
        Objects.requireNonNull(actionType, "actionType");
        int modifiers = actionType.getModifiers();
        if (actionType.isInterface() || Modifier.isAbstract(modifiers) || !Modifier.isFinal(modifiers)) {
            throw configuration("Action type must be concrete and final: " + actionType.getName());
        }
        if (ActionContract.class.isAssignableFrom(actionType) && actionType.isInterface()) {
            throw configuration("Action contract cannot be registered as an action: " + actionType.getName());
        }
    }

    private static Set<Class<? extends ActionContract>> contractsOf(Class<?> actionType) {
        Set<Class<? extends ActionContract>> result =
            new LinkedHashSet<Class<? extends ActionContract>>();
        collectContracts(actionType, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void collectContracts(Class<?> type,
            Set<Class<? extends ActionContract>> result) {
        for (Class<?> interfaceType : type.getInterfaces()) {
            if (interfaceType != ActionContract.class
                    && ActionContract.class.isAssignableFrom(interfaceType)) {
                result.add((Class<? extends ActionContract>) interfaceType);
                collectContracts(interfaceType, result);
            }
        }
        Class<?> parent = type.getSuperclass();
        if (parent != null) {
            collectContracts(parent, result);
        }
    }

    private static MonitoringConfigurationException configuration(String message) {
        return new MonitoringConfigurationException(
            MonitoringErrorCode.CONFLICTING_ACTION_DEFINITION, message);
    }
}
