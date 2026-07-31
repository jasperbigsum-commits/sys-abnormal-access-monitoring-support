package io.github.jasper.monitoring.api.rule;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.action.ActionContract;
import io.github.jasper.monitoring.api.action.ActionDisposition;
import io.github.jasper.monitoring.api.action.ActionRequirement;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Complete immutable static semantics for one typed rule. */
public final class RuleDefinition<R extends RuleType> {
    private final Class<R> type;
    private final String id;
    private final Set<Class<? extends ActionType>> actionTypes;
    private final Set<Class<? extends ActionContract>> actionContracts;
    private final Set<Class<? extends FactType<?>>> requiredFacts;
    private final Map<Class<? extends FactType<?>>, Set<FactSource>> acceptedSources;
    private final Duration historyWindow;
    private final long threshold;
    private final RiskLevel risk;
    private final ActionDisposition disposition;
    private final Set<ActionRequirement> requirements;
    private final Set<ControlActionType> controls;
    private final RuleMode mode;
    private final RuleSource source;

    private RuleDefinition(Builder<R> builder) {
        type = builder.type;
        id = builder.id;
        actionTypes = immutableSet(builder.actionTypes);
        actionContracts = immutableSet(builder.actionContracts);
        requiredFacts = immutableSet(builder.requiredFacts);
        acceptedSources = immutableSources(builder.acceptedSources);
        historyWindow = builder.historyWindow;
        threshold = builder.threshold;
        risk = builder.risk;
        disposition = builder.disposition;
        requirements = immutableSet(builder.requirements);
        controls = immutableSet(builder.controls);
        mode = builder.mode;
        source = builder.source;
    }

    public static <R extends RuleType> Builder<R> builder(Class<R> type, String id) {
        return new Builder<R>(type, id);
    }

    public Class<R> getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public Set<Class<? extends ActionType>> getActionTypes() {
        return actionTypes;
    }

    public Set<Class<? extends ActionContract>> getActionContracts() {
        return actionContracts;
    }

    public Set<Class<? extends FactType<?>>> getRequiredFacts() {
        return requiredFacts;
    }

    public Set<FactSource> getAcceptedSources(Class<? extends FactType<?>> factType) {
        Set<FactSource> sources = acceptedSources.get(factType);
        return sources == null ? Collections.<FactSource>emptySet() : sources;
    }

    public Duration getHistoryWindow() {
        return historyWindow;
    }

    public long getThreshold() {
        return threshold;
    }

    public RiskLevel getRisk() {
        return risk;
    }

    public ActionDisposition getDisposition() {
        return disposition;
    }

    public Set<ActionRequirement> getRequirements() {
        return requirements;
    }

    public Set<ControlActionType> getControls() {
        return controls;
    }

    public RuleMode getMode() {
        return mode;
    }

    public RuleSource getSource() {
        return source;
    }

    private static <T> Set<T> immutableSet(Set<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<T>(values));
    }

    private static Map<Class<? extends FactType<?>>, Set<FactSource>> immutableSources(
            Map<Class<? extends FactType<?>>, Set<FactSource>> values) {
        Map<Class<? extends FactType<?>>, Set<FactSource>> copy =
            new LinkedHashMap<Class<? extends FactType<?>>, Set<FactSource>>();
        for (Map.Entry<Class<? extends FactType<?>>, Set<FactSource>> entry : values.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(EnumSet.copyOf(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    /** Builder requiring every rule-owned runtime prerequisite to be explicit. */
    public static final class Builder<R extends RuleType> {
        private final Class<R> type;
        private final String id;
        private final Set<Class<? extends ActionType>> actionTypes =
            new LinkedHashSet<Class<? extends ActionType>>();
        private final Set<Class<? extends ActionContract>> actionContracts =
            new LinkedHashSet<Class<? extends ActionContract>>();
        private final Set<Class<? extends FactType<?>>> requiredFacts =
            new LinkedHashSet<Class<? extends FactType<?>>>();
        private final Map<Class<? extends FactType<?>>, Set<FactSource>> acceptedSources =
            new LinkedHashMap<Class<? extends FactType<?>>, Set<FactSource>>();
        private Duration historyWindow;
        private long threshold;
        private RiskLevel risk;
        private ActionDisposition disposition = ActionDisposition.ALLOW;
        private final Set<ActionRequirement> requirements = EnumSet.noneOf(ActionRequirement.class);
        private final Set<ControlActionType> controls = EnumSet.noneOf(ControlActionType.class);
        private RuleMode mode;
        private RuleSource source;

        private Builder(Class<R> type, String id) {
            this.type = Objects.requireNonNull(type, "type");
            if (id == null || id.trim().isEmpty() || id.length() > 128) {
                throw new IllegalArgumentException("Rule id must not be blank and must be at most 128 characters");
            }
            this.id = id;
        }

        public Builder<R> appliesTo(Class<? extends ActionType> actionType) {
            actionTypes.add(Objects.requireNonNull(actionType, "actionType"));
            return this;
        }

        public Builder<R> appliesToContract(Class<? extends ActionContract> contractType) {
            actionContracts.add(Objects.requireNonNull(contractType, "contractType"));
            return this;
        }

        public Builder<R> require(Class<? extends FactType<?>> factType, FactSource... sourceValues) {
            Objects.requireNonNull(factType, "factType");
            if (sourceValues == null || sourceValues.length == 0) {
                throw new IllegalArgumentException("At least one accepted fact source is required");
            }
            EnumSet<FactSource> sources = EnumSet.noneOf(FactSource.class);
            for (FactSource factSource : sourceValues) {
                sources.add(Objects.requireNonNull(factSource, "source"));
            }
            Set<FactSource> existing = acceptedSources.get(factType);
            if (existing != null) {
                sources.retainAll(existing);
                if (sources.isEmpty()) {
                    throw new IllegalArgumentException(
                        "Repeated rule fact declarations have no common accepted source");
                }
            }
            requiredFacts.add(factType);
            acceptedSources.put(factType, sources);
            return this;
        }

        public Builder<R> historyWindow(Duration historyWindow) {
            Objects.requireNonNull(historyWindow, "historyWindow");
            if (historyWindow.isNegative()) {
                throw new IllegalArgumentException("historyWindow must not be negative");
            }
            this.historyWindow = historyWindow;
            return this;
        }

        public Builder<R> threshold(long threshold) {
            if (threshold <= 0L) {
                throw new IllegalArgumentException("threshold must be positive");
            }
            this.threshold = threshold;
            return this;
        }

        public Builder<R> risk(RiskLevel risk) {
            this.risk = Objects.requireNonNull(risk, "risk");
            return this;
        }

        public Builder<R> disposition(ActionDisposition disposition) {
            this.disposition = Objects.requireNonNull(disposition, "disposition");
            return this;
        }

        public Builder<R> requirement(ActionRequirement requirement) {
            requirements.add(Objects.requireNonNull(requirement, "requirement"));
            return this;
        }

        public Builder<R> control(ControlActionType control) {
            controls.add(Objects.requireNonNull(control, "control"));
            return this;
        }

        public Builder<R> mode(RuleMode mode) {
            this.mode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        public Builder<R> source(RuleSource source) {
            this.source = Objects.requireNonNull(source, "source");
            return this;
        }

        public RuleDefinition<R> build() {
            if (actionTypes.isEmpty() && actionContracts.isEmpty()) {
                throw new IllegalStateException("A rule must declare at least one action type or contract");
            }
            Objects.requireNonNull(historyWindow, "historyWindow");
            if (threshold <= 0L) {
                throw new IllegalStateException("threshold must be configured");
            }
            Objects.requireNonNull(risk, "risk");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(source, "source");
            return new RuleDefinition<R>(this);
        }
    }
}
