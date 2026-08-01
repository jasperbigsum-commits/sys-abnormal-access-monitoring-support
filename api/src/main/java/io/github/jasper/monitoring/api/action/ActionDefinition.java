package io.github.jasper.monitoring.api.action;

import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.rule.RuleType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable static semantics for one concrete action type. */
public final class ActionDefinition {
    private static final Pattern CODE_PATTERN =
        Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*:[a-z0-9]+(?:-[a-z0-9]+)*");

    private final String code;
    private final SecurityEventType eventType;
    private final Map<SecurityEventResult, SecurityEventType> eventTypes;
    private final String resourceType;
    private final Set<String> ruleTags;
    private final Set<Class<? extends RuleType>> ruleTypes;
    private final Set<Class<? extends FactType<?>>> requiredFacts;
    private final Set<Class<? extends FactType<?>>> optionalFacts;
    private final Map<Class<? extends FactType<?>>, Set<FactSource>> allowedSources;
    private final ActionFailurePolicy failurePolicy;

    private ActionDefinition(Builder builder) {
        this(builder.code, builder.eventType, builder.eventTypes, builder.resourceType, builder.ruleTags,
            builder.ruleTypes, builder.requiredFacts, builder.optionalFacts,
            builder.allowedSources, builder.failurePolicy);
    }

    private ActionDefinition(String code, SecurityEventType eventType,
            Map<SecurityEventResult, SecurityEventType> eventTypes, String resourceType,
            Set<String> ruleTags, Set<Class<? extends RuleType>> ruleTypes,
            Set<Class<? extends FactType<?>>> requiredFacts,
            Set<Class<? extends FactType<?>>> optionalFacts,
            Map<Class<? extends FactType<?>>, Set<FactSource>> allowedSources,
            ActionFailurePolicy failurePolicy) {
        this.code = code;
        this.eventType = eventType;
        EnumMap<SecurityEventResult, SecurityEventType> mappedTypes =
            new EnumMap<SecurityEventResult, SecurityEventType>(SecurityEventResult.class);
        mappedTypes.putAll(eventTypes);
        this.eventTypes = Collections.unmodifiableMap(mappedTypes);
        this.resourceType = resourceType;
        this.ruleTags = immutableSet(ruleTags);
        this.ruleTypes = immutableSet(ruleTypes);
        this.requiredFacts = immutableSet(requiredFacts);
        Set<Class<? extends FactType<?>>> effectiveOptional =
            new LinkedHashSet<Class<? extends FactType<?>>>(optionalFacts);
        effectiveOptional.removeAll(requiredFacts);
        this.optionalFacts = Collections.unmodifiableSet(effectiveOptional);
        this.allowedSources = ActionContractDefinition.immutableSources(allowedSources);
        this.failurePolicy = failurePolicy;
    }

    /** @param code stable lowercase {@code domain:verb} code */
    public static Builder builder(String code) {
        return new Builder(code);
    }

    public String getCode() {
        return code;
    }

    public SecurityEventType getEventType() {
        return eventType;
    }

    public SecurityEventType resolveEventType(SecurityEventResult result) {
        SecurityEventType resolved = eventTypes.get(Objects.requireNonNull(result, "result"));
        return resolved == null ? eventType : resolved;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Set<String> getRuleTags() {
        return ruleTags;
    }

    public Set<Class<? extends RuleType>> getRuleTypes() {
        return ruleTypes;
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

    public ActionFailurePolicy getFailurePolicy() {
        return failurePolicy;
    }

    ActionDefinition merge(ActionContractDefinition contract) {
        if (!failurePolicy.isAtLeast(contract.getMinimumFailurePolicy())) {
            throw configuration("Action failure policy " + failurePolicy
                + " weakens contract minimum " + contract.getMinimumFailurePolicy());
        }
        Set<Class<? extends FactType<?>>> mergedRequired = union(requiredFacts, contract.getRequiredFacts());
        Set<Class<? extends FactType<?>>> mergedOptional = union(optionalFacts, contract.getOptionalFacts());
        mergedOptional.removeAll(mergedRequired);
        Set<Class<? extends RuleType>> mergedRules = union(ruleTypes, contract.getRuleTypes());
        Map<Class<? extends FactType<?>>, Set<FactSource>> mergedSources =
            new LinkedHashMap<Class<? extends FactType<?>>, Set<FactSource>>(allowedSources);
        for (Map.Entry<Class<? extends FactType<?>>, Set<FactSource>> entry
                : contract.getAllowedSources().entrySet()) {
            Set<FactSource> existing = mergedSources.get(entry.getKey());
            if (existing == null) {
                mergedSources.put(entry.getKey(), entry.getValue());
            } else {
                EnumSet<FactSource> intersection = EnumSet.copyOf(existing);
                intersection.retainAll(entry.getValue());
                if (intersection.isEmpty()) {
                    throw configuration("Action and contract have no common source for fact "
                        + entry.getKey().getName());
                }
                mergedSources.put(entry.getKey(), intersection);
            }
        }
        return new ActionDefinition(code, eventType, eventTypes, resourceType, ruleTags, mergedRules,
            mergedRequired, mergedOptional, mergedSources,
            ActionFailurePolicy.strictest(failurePolicy, contract.getMinimumFailurePolicy()));
    }

    private static MonitoringConfigurationException configuration(String message) {
        return new MonitoringConfigurationException(
            MonitoringErrorCode.CONFLICTING_ACTION_DEFINITION, message);
    }

    private static <T> Set<T> union(Set<T> first, Set<T> second) {
        Set<T> result = new LinkedHashSet<T>(first);
        result.addAll(second);
        return result;
    }

    private static <T> Set<T> immutableSet(Set<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<T>(values));
    }

    /** Builder for an immutable action definition. */
    public static final class Builder {
        private final String code;
        private SecurityEventType eventType;
        private final Map<SecurityEventResult, SecurityEventType> eventTypes =
            new EnumMap<SecurityEventResult, SecurityEventType>(SecurityEventResult.class);
        private String resourceType;
        private final Set<String> ruleTags = new LinkedHashSet<String>();
        private final Set<Class<? extends RuleType>> ruleTypes =
            new LinkedHashSet<Class<? extends RuleType>>();
        private final Set<Class<? extends FactType<?>>> requiredFacts =
            new LinkedHashSet<Class<? extends FactType<?>>>();
        private final Set<Class<? extends FactType<?>>> optionalFacts =
            new LinkedHashSet<Class<? extends FactType<?>>>();
        private final Map<Class<? extends FactType<?>>, Set<FactSource>> allowedSources =
            new LinkedHashMap<Class<? extends FactType<?>>, Set<FactSource>>();
        private ActionFailurePolicy failurePolicy;

        private Builder(String code) {
            this.code = validateCode(code);
        }

        public Builder eventType(SecurityEventType eventType) {
            this.eventType = Objects.requireNonNull(eventType, "eventType");
            return this;
        }

        public Builder eventTypeFor(SecurityEventResult result, SecurityEventType eventType) {
            eventTypes.put(Objects.requireNonNull(result, "result"),
                Objects.requireNonNull(eventType, "eventType"));
            return this;
        }

        public Builder resourceType(String resourceType) {
            if (resourceType == null || resourceType.trim().isEmpty()) {
                throw new IllegalArgumentException("resourceType must not be blank");
            }
            this.resourceType = resourceType;
            return this;
        }

        public Builder ruleTag(String ruleTag) {
            if (ruleTag == null || ruleTag.trim().isEmpty()) {
                throw new IllegalArgumentException("ruleTag must not be blank");
            }
            ruleTags.add(ruleTag);
            return this;
        }

        public Builder participateIn(Class<? extends RuleType> ruleType) {
            ruleTypes.add(Objects.requireNonNull(ruleType, "ruleType"));
            return this;
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

        public Builder failurePolicy(ActionFailurePolicy failurePolicy) {
            this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
            return this;
        }

        public ActionDefinition build() {
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(resourceType, "resourceType");
            Objects.requireNonNull(failurePolicy, "failurePolicy");
            return new ActionDefinition(this);
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

        private static String validateCode(String code) {
            Objects.requireNonNull(code, "code");
            if (code.length() > 128 || !CODE_PATTERN.matcher(code).matches()) {
                throw new IllegalArgumentException(
                    "Action code must be lowercase domain:verb, use only alphanumerics and internal hyphens, and be at most 128 characters");
            }
            return code;
        }
    }
}
