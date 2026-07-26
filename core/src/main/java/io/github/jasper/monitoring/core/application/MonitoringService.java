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
    private final RuleEvaluationPort evaluator;

    public MonitoringService(MonitoringRepository repository, SecurityEventAssembler assembler,
                             RuleEvaluationPort evaluator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.assembler = Objects.requireNonNull(assembler, "assembler");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    public SecurityEventAssembler.AssemblyResult monitor(ActionDefinition action,
                                                         ActionExecution execution,
                                                         ActionFacts facts) {
        SecurityEventAssembler.AssemblyResult result = assembler.assemble(action, execution, facts);
        repository.saveEvent(result.getEvent());
        evaluator.evaluate(result.getEvent(), result.getIssues());
        return result;
    }

    @FunctionalInterface
    public interface RuleEvaluationPort {
        void evaluate(SecurityEvent event, java.util.List<io.github.jasper.monitoring.api.event.ObservationIssue> issues);
    }
}
