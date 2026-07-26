package io.github.jasper.monitoring.api.fact;

import io.github.jasper.monitoring.api.action.ActionContract;
import io.github.jasper.monitoring.api.action.ActionType;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Owns the explicit relationship between one provider, a target, and declared facts. */
public final class FactBinding {
    private final Class<? extends ActionType> actionType;
    private final Class<? extends ActionContract> contractType;
    private final ActionFactProvider provider;
    private final FactSource source;
    private final Set<Class<? extends FactType<?>>> declaredFacts;

    private FactBinding(Class<? extends ActionType> actionType,
            Class<? extends ActionContract> contractType, FactSource source, ActionFactProvider provider,
            Class<? extends FactType<?>>[] declaredFacts) {
        this.actionType = actionType;
        this.contractType = contractType;
        this.provider = Objects.requireNonNull(provider, "provider");
        this.source = Objects.requireNonNull(source, "source");
        if (declaredFacts == null || declaredFacts.length == 0) {
            throw new IllegalArgumentException("At least one declared fact is required");
        }
        LinkedHashSet<Class<? extends FactType<?>>> facts =
            new LinkedHashSet<Class<? extends FactType<?>>>(Arrays.asList(declaredFacts));
        if (facts.contains(null)) {
            throw new NullPointerException("declaredFacts contains null");
        }
        this.declaredFacts = Collections.unmodifiableSet(facts);
    }

    /** Creates an exact action binding that is never inherited by sibling actions. */
    @SafeVarargs
    public static FactBinding forAction(Class<? extends ActionType> actionType,
            FactSource source, ActionFactProvider provider,
            Class<? extends FactType<?>>... declaredFacts) {
        Objects.requireNonNull(actionType, "actionType");
        int modifiers = actionType.getModifiers();
        if (actionType.isInterface() || Modifier.isAbstract(modifiers) || !Modifier.isFinal(modifiers)) {
            throw new IllegalArgumentException("Action binding target must be concrete and final");
        }
        return new FactBinding(actionType, null, source, provider, declaredFacts);
    }

    /** Creates an explicit contract binding shared by every action implementing that contract. */
    @SafeVarargs
    public static FactBinding forContract(Class<? extends ActionContract> contractType,
            FactSource source, ActionFactProvider provider,
            Class<? extends FactType<?>>... declaredFacts) {
        Objects.requireNonNull(contractType, "contractType");
        if (contractType == ActionContract.class || !contractType.isInterface()) {
            throw new IllegalArgumentException("Contract binding target must be a specific contract interface");
        }
        return new FactBinding(null, contractType, source, provider, declaredFacts);
    }

    /** @return whether this binding explicitly covers the supplied concrete action */
    public boolean appliesTo(Class<? extends ActionType> candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return actionType != null ? actionType.equals(candidate) : contractType.isAssignableFrom(candidate);
    }

    public ActionFactProvider getProvider() {
        return provider;
    }

    public FactSource getSource() {
        return source;
    }

    public Set<Class<? extends FactType<?>>> getDeclaredFacts() {
        return declaredFacts;
    }

    public Class<? extends ActionType> getActionType() {
        return actionType;
    }

    public Class<? extends ActionContract> getContractType() {
        return contractType;
    }
}
