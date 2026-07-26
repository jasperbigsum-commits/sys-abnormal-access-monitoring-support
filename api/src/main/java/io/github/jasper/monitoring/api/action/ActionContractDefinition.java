package io.github.jasper.monitoring.api.action;

import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.rule.RuleType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable inheritable requirements attached to an {@link ActionContract}. */
public final class ActionContractDefinition {
    private final Set<Class<? extends FactType<?>>> requiredFacts;
    private final Set<Class<? extends FactType<?>>> optionalFacts;
    private final Map<Class<? extends FactType<?>>, Set<FactSource>> allowedSources;
    private final Set<Class<? extends RuleType>> ruleTypes;
    private final ActionFailurePolicy minimumFailurePolicy;

    private ActionContractDefinition(Builder builder) {
        this.requiredFacts = immutableSet(builder.requiredFacts);
        Set<Class<? extends FactType<?>>> optional =
            new LinkedHashSet<Class<? extends FactType<?>>>(builder.optionalFacts);
        optional.removeAll(requiredFacts);
        this.optionalFacts = Collections.unmodifiableSet(optional);
        this.allowedSources = immutableSources(builder.allowedSources);
        this.ruleTypes = immutableSet(builder.ruleTypes);
        this.minimumFailurePolicy = builder.minimumFailurePolicy;
    }

    /** @return a contract definition builder */
    public static Builder builder() {
        return new Builder();
    }

    public Set<Class<? extends FactType<?>>> getRequiredFacts() {
        return requiredFacts;
    }

    public Set<Class<? extends FactType<?>>> getOptionalFacts() {
        return optionalFacts;
    }

    public Set<FactSource> getAllowedSources(Class<? extends FactType<?>> factType) {
        Set<FactSource> sources = allowedSources.get(factType);
        return sources == null ? Collections.<FactSource>emptySet() : sources;
    }

    Map<Class<? extends FactType<?>>, Set<FactSource>> getAllowedSources() {
        return allowedSources;
    }

    public Set<Class<? extends RuleType>> getRuleTypes() {
        return ruleTypes;
    }

    public ActionFailurePolicy getMinimumFailurePolicy() {
        return minimumFailurePolicy;
    }

    private static <T> Set<T> immutableSet(Set<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<T>(values));
    }

    static Map<Class<? extends FactType<?>>, Set<FactSource>> immutableSources(
            Map<Class<? extends FactType<?>>, Set<FactSource>> sources) {
        Map<Class<? extends FactType<?>>, Set<FactSource>> copy =
            new LinkedHashMap<Class<? extends FactType<?>>, Set<FactSource>>();
        for (Map.Entry<Class<? extends FactType<?>>, Set<FactSource>> entry : sources.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(EnumSet.copyOf(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    /** Builder for an immutable action contract definition. */
    public static final class Builder {
        private final Set<Class<? extends FactType<?>>> requiredFacts =
            new LinkedHashSet<Class<? extends FactType<?>>>();
        private final Set<Class<? extends FactType<?>>> optionalFacts =
            new LinkedHashSet<Class<? extends FactType<?>>>();
        private final Map<Class<? extends FactType<?>>, Set<FactSource>> allowedSources =
            new LinkedHashMap<Class<? extends FactType<?>>, Set<FactSource>>();
        private final Set<Class<? extends RuleType>> ruleTypes =
            new LinkedHashSet<Class<? extends RuleType>>();
        private ActionFailurePolicy minimumFailurePolicy = ActionFailurePolicy.OBSERVE_ONLY;

        private Builder() {
        }

        public Builder require(Class<? extends FactType<?>> factType, FactSource... sources) {
            requiredFacts.add(Objects.requireNonNull(factType, "factType"));
            optionalFacts.remove(factType);
            putSources(factType, sources);
            return this;
        }

        public Builder optional(Class<? extends FactType<?>> factType, FactSource... sources) {
            Objects.requireNonNull(factType, "factType");
            if (!requiredFacts.contains(factType)) {
                optionalFacts.add(factType);
            }
            putSources(factType, sources);
            return this;
        }

        public Builder participateIn(Class<? extends RuleType> ruleType) {
            ruleTypes.add(Objects.requireNonNull(ruleType, "ruleType"));
            return this;
        }

        public Builder minimumFailurePolicy(ActionFailurePolicy policy) {
            this.minimumFailurePolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public ActionContractDefinition build() {
            return new ActionContractDefinition(this);
        }

        private void putSources(Class<? extends FactType<?>> factType, FactSource[] sourceValues) {
            if (sourceValues == null || sourceValues.length == 0) {
                throw new IllegalArgumentException("At least one fact source is required");
            }
            EnumSet<FactSource> sources = EnumSet.noneOf(FactSource.class);
            for (FactSource source : sourceValues) {
                sources.add(Objects.requireNonNull(source, "source"));
            }
            Set<FactSource> existing = allowedSources.get(factType);
            if (existing != null) {
                sources.retainAll(existing);
                if (sources.isEmpty()) {
                    throw new IllegalArgumentException(
                        "Repeated fact declarations have no common allowed source");
                }
            }
            allowedSources.put(factType, sources);
        }
    }
}
