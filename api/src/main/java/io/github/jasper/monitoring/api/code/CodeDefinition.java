package io.github.jasper.monitoring.api.code;

import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.action.ActionContract;
import io.github.jasper.monitoring.api.action.ActionType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/** Immutable applicability metadata for one stable code. */
public final class CodeDefinition {
    private final GovernedCode code;
    private final Set<SecurityEventResult> outcomes;
    private final Set<Class<? extends ActionType>> actionTypes;
    private final Set<Class<? extends ActionContract>> contracts;

    private CodeDefinition(Builder builder) {
        this.code = builder.code;
        this.outcomes = immutableEnum(builder.outcomes);
        this.actionTypes = Collections.unmodifiableSet(
            new LinkedHashSet<Class<? extends ActionType>>(builder.actionTypes));
        this.contracts = Collections.unmodifiableSet(
            new LinkedHashSet<Class<? extends ActionContract>>(builder.contracts));
    }

    public static Builder reason(ReasonCode code) {
        return new Builder(code);
    }

    public static Builder of(GovernedCode code) {
        return new Builder(code);
    }

    public GovernedCode getCodeDefinition() {
        return code;
    }

    public String getCode() {
        return code.getCode();
    }

    public CodeFamily getFamily() {
        return code.getFamily();
    }

    public Set<SecurityEventResult> getAllowedOutcomes() {
        return outcomes;
    }

    public Set<Class<? extends ActionType>> getActionTypes() {
        return actionTypes;
    }

    public Set<Class<? extends ActionContract>> getContracts() {
        return contracts;
    }

    public boolean appliesTo(Class<? extends ActionType> actionType) {
        if (actionTypes.contains(actionType)) {
            return true;
        }
        for (Class<? extends ActionContract> contract : contracts) {
            if (contract.isAssignableFrom(actionType)) {
                return true;
            }
        }
        return false;
    }

    public static final class Builder {
        private final GovernedCode code;
        private final EnumSet<SecurityEventResult> outcomes =
            EnumSet.noneOf(SecurityEventResult.class);
        private final Set<Class<? extends ActionType>> actionTypes =
            new LinkedHashSet<Class<? extends ActionType>>();
        private final Set<Class<? extends ActionContract>> contracts =
            new LinkedHashSet<Class<? extends ActionContract>>();

        private Builder(GovernedCode code) {
            this.code = requireNonNull(code, "code");
        }

        public Builder allow(SecurityEventResult... values) {
            if (values == null || values.length == 0) {
                throw new IllegalArgumentException("At least one outcome is required");
            }
            for (SecurityEventResult value : values) {
                outcomes.add(requireNonNull(value, "outcome"));
            }
            return this;
        }

        @SafeVarargs
        public final Builder appliesTo(Class<? extends ActionType>... values) {
            if (values == null || values.length == 0) {
                throw new IllegalArgumentException("At least one action type is required");
            }
            for (Class<? extends ActionType> value : values) {
                actionTypes.add(requireNonNull(value, "actionType"));
            }
            return this;
        }

        @SafeVarargs
        public final Builder appliesToContract(Class<? extends ActionContract>... values) {
            if (values == null || values.length == 0) {
                throw new IllegalArgumentException("At least one action contract is required");
            }
            for (Class<? extends ActionContract> value : values) {
                contracts.add(requireNonNull(value, "contract"));
            }
            return this;
        }

        public CodeDefinition build() {
            if (outcomes.isEmpty()) {
                throw new IllegalStateException("At least one allowed outcome is required");
            }
            if (actionTypes.isEmpty() && contracts.isEmpty()) {
                throw new IllegalStateException("At least one action scope is required");
            }
            return new CodeDefinition(this);
        }
    }

    private static Set<SecurityEventResult> immutableEnum(EnumSet<SecurityEventResult> values) {
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }
}
