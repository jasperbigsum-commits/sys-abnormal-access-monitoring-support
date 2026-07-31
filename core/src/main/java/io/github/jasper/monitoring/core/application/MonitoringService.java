package io.github.jasper.monitoring.core.application;

import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.port.EventRepository;
import java.util.Objects;

/** Application entry point: assemble, persist, then delegate eligible rule evaluation. */
public final class MonitoringService {
    private final EventRepository repository;
    private final SecurityEventAssembler assembler;
    private final MonitoringRuntimePort runtime;
    private final RuleEvaluationPort evaluator;

    public MonitoringService(EventRepository repository, SecurityEventAssembler assembler,
                             MonitoringRuntimePort runtime, RuleEvaluationPort evaluator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    public SecurityEventAssembler.AssemblyResult monitor(ActionExecution execution) {
        Objects.requireNonNull(execution, "execution");
        ActionDefinition action = runtime.resolve(execution.getActionType());
        MonitoringRuntimePort.FactCollection collected = runtime.collect(execution, action);
        ActionFacts facts = collected.getFacts();
        SecurityEventAssembler.AssemblyResult result = assembler.assemble(execution.getActionType(), action, execution,
            facts, collected.getPersistedFacts());
        repository.save(result.getEvent());
        evaluator.evaluate(execution.getActionType(), action, result.getEvent(), result.getFacts(), collected.getSources(),
            result.getIneligibleRuleTypes(), result.getIssues());
        return result;
    }

    /** Evaluates completed runtime facts without persisting the candidate event. */
    public ActionDecision decide(ActionExecution execution) {
        Objects.requireNonNull(execution, "execution");
        ActionDefinition action = runtime.resolve(execution.getActionType());
        MonitoringRuntimePort.FactCollection collected = runtime.collect(execution, action);
        SecurityEventAssembler.AssemblyResult result = assembler.assemble(execution.getActionType(), action,
            execution, collected.getFacts(), collected.getPersistedFacts());
        return evaluator.decide(execution.getActionType(), action, result.getEvent(), result.getFacts(),
            collected.getSources(), result.getIneligibleRuleTypes(), result.getIssues());
    }

    @FunctionalInterface
    public interface RuleEvaluationPort {
        void evaluate(Class<? extends io.github.jasper.monitoring.api.action.ActionType> actionType,
                      ActionDefinition action, SecurityEvent event, ActionFacts facts,
                      java.util.Map<Class<? extends FactType<?>>, FactSource> factSources,
                      java.util.Set<Class<? extends io.github.jasper.monitoring.api.rule.RuleType>> ineligibleRuleTypes,
                      java.util.List<io.github.jasper.monitoring.api.event.ObservationIssue> issues);

        default ActionDecision decide(
                Class<? extends io.github.jasper.monitoring.api.action.ActionType> actionType,
                ActionDefinition action, SecurityEvent event, ActionFacts facts,
                java.util.Map<Class<? extends FactType<?>>, FactSource> factSources,
                java.util.Set<Class<? extends io.github.jasper.monitoring.api.rule.RuleType>> ineligibleRuleTypes,
                java.util.List<io.github.jasper.monitoring.api.event.ObservationIssue> issues) {
            return ActionDecision.allow();
        }
    }

}
