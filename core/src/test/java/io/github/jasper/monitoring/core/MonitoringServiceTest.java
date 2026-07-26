package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionFailurePolicy;
import io.github.jasper.monitoring.api.action.ActionCatalog;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.rule.RuleType;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import io.github.jasper.monitoring.core.infrastructure.memory.InMemoryMonitoringRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

class MonitoringServiceTest {
    @Test
    void persistsEventBeforeEvaluatingRules() {
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        AtomicBoolean persisted = new AtomicBoolean(false);
        ActionDefinition action = ActionDefinition.builder("demo:query").eventType(SecurityEventType.QUERY)
            .resourceType("report").failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
        ActionCatalog catalog = new ActionCatalog();
        catalog.register(QueryAction.class, action);
        catalog.freeze();
        MonitoringService service = new MonitoringService(repository,
            new SecurityEventAssembler("demo", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            new MonitoringRuntimePort() {
                public ActionDefinition resolve(Class<? extends ActionType> type) { return catalog.require(type); }
                public ActionFacts collect(ActionExecution execution, ActionDefinition definition) { return ActionFacts.builder().build(); }
            },
            (event, facts, ineligible, issues) -> persisted.set(!repository.findEventsSince(Instant.EPOCH).isEmpty()));
        service.monitor(ActionExecution.of(QueryAction.class, request(), IdentityContext.anonymous(), ActionOutcome.success()));
        assertTrue(persisted.get());
    }

    @Test
    void rejectsExecutionWhenTypedActionIsNotRegistered() {
        ActionCatalog catalog = new ActionCatalog();
        catalog.freeze();
        MonitoringService service = new MonitoringService(new InMemoryMonitoringRepository(),
            new SecurityEventAssembler("demo", Clock.systemUTC()),
            new MonitoringRuntimePort() {
                public ActionDefinition resolve(Class<? extends ActionType> type) { return catalog.require(type); }
                public ActionFacts collect(ActionExecution execution, ActionDefinition definition) { return ActionFacts.builder().build(); }
            }, (event, facts, ineligible, issues) -> { });
        assertThrows(RuntimeException.class, () -> service.monitor(
            ActionExecution.of(QueryAction.class, request(), IdentityContext.anonymous(), ActionOutcome.success())));
    }

    @Test
    void exposesCollectedFactsAndMarksRuleTypesIneligibleWhenRequiredFactMissing() {
        ActionDefinition action = ActionDefinition.builder("demo:query").eventType(SecurityEventType.QUERY)
            .resourceType("report").require(RequiredFact.class, FactSource.HOST_PROVIDER)
            .participateIn(QueryRule.class).failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
        ActionCatalog catalog = new ActionCatalog();
        catalog.register(QueryAction.class, action);
        catalog.freeze();
        ActionFacts facts = ActionFacts.builder().build();
        final ActionFacts[] seen = new ActionFacts[1];
        final java.util.Set<?>[] ineligible = new java.util.Set<?>[1];
        MonitoringService service = new MonitoringService(new InMemoryMonitoringRepository(),
            new SecurityEventAssembler("demo", Clock.systemUTC()),
            new MonitoringRuntimePort() {
                public ActionDefinition resolve(Class<? extends ActionType> type) { return catalog.require(type); }
                public ActionFacts collect(ActionExecution execution, ActionDefinition definition) { return facts; }
            },
            (event, evaluatedFacts, skipped, issues) -> { seen[0] = evaluatedFacts; ineligible[0] = skipped; });
        service.monitor(ActionExecution.of(QueryAction.class, request(), IdentityContext.anonymous(), ActionOutcome.success()));
        assertSame(facts, seen[0]);
        assertTrue(ineligible[0].contains(QueryRule.class));
    }

    private static MonitoringRequestContext request() {
        return MonitoringRequestContext.builder().method("GET").path("/reports").sourceIp("127.0.0.1")
            .requestId("req-1").build();
    }
    static final class QueryAction implements ActionType { }
    static final class RequiredFact implements FactType<String> { }
    static final class QueryRule implements RuleType { }
}
