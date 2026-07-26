package io.github.jasper.monitoring.core.domain.rule;

import io.github.jasper.monitoring.api.action.ActionContract;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.rule.RuleDefinition;
import io.github.jasper.monitoring.api.rule.RuleType;
import io.github.jasper.monitoring.core.domain.RuleMatch;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable rule input and the sole owner of typed prerequisite evaluation. */
public final class RuleEvaluationContext {
    public enum Status {
        EVALUATED,
        SKIPPED_MISSING_FACT,
        SKIPPED_NOT_APPLICABLE
    }

    private final SecurityEvent event;
    private final List<SecurityEvent> history;
    private final Class<? extends ActionType> actionType;
    private final ActionDefinition actionDefinition;
    private final ActionFacts facts;
    private final Map<Class<? extends FactType<?>>, FactSource> factSources;

    private RuleEvaluationContext(Builder builder) {
        event = builder.event;
        history = Collections.unmodifiableList(new ArrayList<SecurityEvent>(builder.history));
        actionType = builder.actionType;
        actionDefinition = builder.actionDefinition;
        facts = builder.facts;
        factSources = Collections.unmodifiableMap(
            new LinkedHashMap<Class<? extends FactType<?>>, FactSource>(builder.factSources));
    }

    public static Builder builder(SecurityEvent event, Class<? extends ActionType> actionType,
            ActionDefinition actionDefinition) {
        return new Builder(event, actionType, actionDefinition);
    }

    public <R extends RuleType> Evaluation evaluate(DetectionRule<R> rule) {
        Objects.requireNonNull(rule, "rule");
        RuleDefinition<R> definition = Objects.requireNonNull(rule.definition(), "rule definition");
        if (!definitionApplies(definition)
                || (!actionDefinition.getRuleTypes().isEmpty()
                    && !actionDefinition.getRuleTypes().contains(definition.getType()))) {
            return Evaluation.skipped(Status.SKIPPED_NOT_APPLICABLE,
                "RULE_ACTION_NOT_APPLICABLE");
        }
        for (Class<? extends FactType<?>> factType : definition.getRequiredFacts()) {
            if (!facts.asMap().containsKey(factType)) {
                return Evaluation.skipped(Status.SKIPPED_MISSING_FACT, "RULE_FACT_MISSING");
            }
            FactSource factSource = factSources.get(factType);
            if (factSource == null
                    || !actionDefinition.getAllowedSources(factType).contains(factSource)
                    || !definition.getAcceptedSources(factType).contains(factSource)) {
                return Evaluation.skipped(Status.SKIPPED_MISSING_FACT,
                    "RULE_FACT_SOURCE_NOT_ACCEPTED");
            }
        }
        return Evaluation.evaluated(rule.evaluate(this));
    }

    private boolean definitionApplies(RuleDefinition<?> definition) {
        if (definition.getActionTypes().contains(actionType)) {
            return true;
        }
        for (Class<? extends ActionContract> contract : definition.getActionContracts()) {
            if (contract.isAssignableFrom(actionType)) {
                return true;
            }
        }
        return false;
    }

    public SecurityEvent getEvent() {
        return event;
    }

    public List<SecurityEvent> getHistory() {
        return history;
    }

    public Class<? extends ActionType> getActionType() {
        return actionType;
    }

    public ActionFacts getFacts() {
        return facts;
    }

    public FactSource getFactSource(Class<? extends FactType<?>> factType) {
        return factSources.get(factType);
    }

    public static final class Evaluation {
        private final Status status;
        private final String diagnosticCode;
        private final Optional<RuleMatch> match;

        private Evaluation(Status status, String diagnosticCode, Optional<RuleMatch> match) {
            this.status = status;
            this.diagnosticCode = diagnosticCode;
            this.match = match;
        }

        private static Evaluation evaluated(Optional<RuleMatch> match) {
            return new Evaluation(Status.EVALUATED, null,
                Objects.requireNonNull(match, "rule evaluation result"));
        }

        private static Evaluation skipped(Status status, String diagnosticCode) {
            return new Evaluation(status, diagnosticCode, Optional.<RuleMatch>empty());
        }

        public Status getStatus() {
            return status;
        }

        public String getDiagnosticCode() {
            return diagnosticCode;
        }

        public Optional<RuleMatch> getMatch() {
            return match;
        }
    }

    public static final class Builder {
        private final SecurityEvent event;
        private final Class<? extends ActionType> actionType;
        private final ActionDefinition actionDefinition;
        private List<SecurityEvent> history = Collections.emptyList();
        private ActionFacts facts = ActionFacts.builder().build();
        private final Map<Class<? extends FactType<?>>, FactSource> factSources =
            new LinkedHashMap<Class<? extends FactType<?>>, FactSource>();

        private Builder(SecurityEvent event, Class<? extends ActionType> actionType,
                ActionDefinition actionDefinition) {
            this.event = Objects.requireNonNull(event, "event");
            this.actionType = Objects.requireNonNull(actionType, "actionType");
            this.actionDefinition = Objects.requireNonNull(actionDefinition, "actionDefinition");
        }

        public Builder history(List<SecurityEvent> history) {
            this.history = new ArrayList<SecurityEvent>(Objects.requireNonNull(history, "history"));
            return this;
        }

        public Builder facts(ActionFacts facts) {
            this.facts = Objects.requireNonNull(facts, "facts");
            return this;
        }

        public Builder factSource(Class<? extends FactType<?>> factType, FactSource source) {
            factSources.put(Objects.requireNonNull(factType, "factType"),
                Objects.requireNonNull(source, "source"));
            return this;
        }

        public RuleEvaluationContext build() {
            return new RuleEvaluationContext(this);
        }
    }
}
