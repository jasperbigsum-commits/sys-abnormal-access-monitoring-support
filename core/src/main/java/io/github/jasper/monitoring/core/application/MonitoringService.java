package io.github.jasper.monitoring.core.application;

import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.port.MonitoringRepository;
import java.util.Objects;

/** Application entry point: assemble, persist, then delegate eligible rule evaluation. */
public final class MonitoringService {
    private final MonitoringRepository repository;
    private final SecurityEventAssembler assembler;
    private final MonitoringRuntimePort runtime;
    private final RuleEvaluationPort evaluator;

    public MonitoringService(MonitoringRepository repository, SecurityEventAssembler assembler,
                             MonitoringRuntimePort runtime, RuleEvaluationPort evaluator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    public SecurityEventAssembler.AssemblyResult monitor(ActionExecution execution) {
        Objects.requireNonNull(execution, "execution");
        ActionDefinition action = runtime.resolve(execution.getActionType());
        ActionFacts facts = runtime.collect(execution, action);
        SecurityEventAssembler.AssemblyResult result = assembler.assemble(action, execution, facts);
        repository.saveEvent(result.getEvent());
        evaluator.evaluate(result.getEvent(), result.getFacts(), result.getIneligibleRuleTypes(), result.getIssues());
        return result;
    }

    @FunctionalInterface
    public interface RuleEvaluationPort {
        void evaluate(SecurityEvent event, ActionFacts facts,
                      java.util.Set<Class<? extends io.github.jasper.monitoring.api.rule.RuleType>> ineligibleRuleTypes,
                      java.util.List<io.github.jasper.monitoring.api.event.ObservationIssue> issues);
    }

}
