package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.action.ActionDisposition;
import io.github.jasper.monitoring.api.action.ActionFailurePolicy;
import io.github.jasper.monitoring.api.action.ActionCatalog;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.api.code.StableCodeCatalog;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.rule.RuleType;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.MonitoringRuntimePort;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.port.EventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MonitoringServiceTest {
    @Test
    void rejectsReasonThatDoesNotApplyToTheAction() {
        RecordingEventRepository repository = new RecordingEventRepository();
        ActionDefinition action = ActionDefinition.builder("demo:query")
            .eventType(SecurityEventType.QUERY).resourceType("report")
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
        ActionCatalog catalog = new ActionCatalog();
        catalog.register(QueryAction.class, action);
        catalog.freeze();
        StableCodeCatalog codes = new StableCodeCatalog("");
        BuiltInReasonCodes.registerInto(codes);
        codes.freeze();
        MonitoringService service = new MonitoringService(repository,
            new SecurityEventAssembler("demo", Clock.systemUTC()),
            new MonitoringRuntimePort() {
                public ActionDefinition resolve(Class<? extends ActionType> type) { return catalog.require(type); }
                public MonitoringRuntimePort.FactCollection collect(ActionExecution execution,
                        ActionDefinition definition) {
                    return MonitoringRuntimePort.FactCollection.empty();
                }
            }, (type, definition, event, facts, sources, ineligible, issues) -> { }, codes);

        assertThrows(MonitoringConfigurationException.class, () -> service.monitor(
            ActionExecution.of(QueryAction.class, request(), IdentityContext.anonymous(),
                ActionOutcome.denied(BuiltInReasonCodes.Authentication.INVALID_CREDENTIAL, 1L))));
        assertTrue(repository.events.isEmpty());
    }

    @Test
    void decidesFromCompletedFactsWithoutPersistingACandidateEvent() {
        RecordingEventRepository repository = new RecordingEventRepository();
        ActionDefinition action = ActionDefinition.builder("demo:query").eventType(SecurityEventType.QUERY)
            .resourceType("report").failurePolicy(ActionFailurePolicy.FAIL_CLOSED).build();
        ActionCatalog catalog = new ActionCatalog();
        catalog.register(QueryAction.class, action);
        catalog.freeze();
        MonitoringService service = new MonitoringService(repository,
            new SecurityEventAssembler("demo", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            new MonitoringRuntimePort() {
                public ActionDefinition resolve(Class<? extends ActionType> type) { return catalog.require(type); }
                public MonitoringRuntimePort.FactCollection collect(ActionExecution execution, ActionDefinition definition) {
                    return MonitoringRuntimePort.FactCollection.empty();
                }
            }, new MonitoringService.RuleEvaluationPort() {
                @Override public void evaluate(Class<? extends ActionType> type, ActionDefinition definition,
                        SecurityEvent event, ActionFacts facts,
                        java.util.Map<Class<? extends FactType<?>>, FactSource> sources,
                        java.util.Set<Class<? extends RuleType>> ineligible,
                        java.util.List<io.github.jasper.monitoring.api.event.ObservationIssue> issues) { }

                @Override public ActionDecision decide(Class<? extends ActionType> type,
                        ActionDefinition definition, SecurityEvent event, ActionFacts facts,
                        java.util.Map<Class<? extends FactType<?>>, FactSource> sources,
                        java.util.Set<Class<? extends RuleType>> ineligible,
                        java.util.List<io.github.jasper.monitoring.api.event.ObservationIssue> issues) {
                    return ActionDecision.blocked("TEST-01");
                }
            }, codes());

        ActionDecision decision = service.decide(ActionExecution.of(QueryAction.class, request(),
            IdentityContext.anonymous(), ActionOutcome.success(0L)));

        assertEquals(ActionDisposition.BLOCK, decision.getDisposition());
        assertTrue(repository.events.isEmpty());
    }
    @Test
    void persistsEventBeforeEvaluatingRules() {
        RecordingEventRepository repository = new RecordingEventRepository();
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
                public MonitoringRuntimePort.FactCollection collect(ActionExecution execution, ActionDefinition definition) {
                    return MonitoringRuntimePort.FactCollection.empty();
                }
            },
            (type, definition, event, facts, sources, ineligible, issues) ->
                persisted.set(!repository.findSince("demo", Instant.EPOCH).isEmpty()), codes());
        service.monitor(ActionExecution.of(QueryAction.class, request(), IdentityContext.anonymous(), ActionOutcome.success(1L)));
        assertTrue(persisted.get());
    }

    @Test
    void recordPersistsWithoutEvaluatingRules() {
        RecordingEventRepository repository = new RecordingEventRepository();
        AtomicBoolean evaluated = new AtomicBoolean(false);
        ActionDefinition action = ActionDefinition.builder("demo:query").eventType(SecurityEventType.QUERY)
            .resourceType("report").failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
        ActionCatalog catalog = new ActionCatalog();
        catalog.register(QueryAction.class, action);
        catalog.freeze();
        MonitoringService service = new MonitoringService(repository,
            new SecurityEventAssembler("demo", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            new MonitoringRuntimePort() {
                public ActionDefinition resolve(Class<? extends ActionType> type) { return catalog.require(type); }
                public MonitoringRuntimePort.FactCollection collect(ActionExecution execution, ActionDefinition definition) {
                    return MonitoringRuntimePort.FactCollection.empty();
                }
            }, (type, definition, event, facts, sources, ineligible, issues) -> evaluated.set(true), codes());

        service.record(ActionExecution.of(QueryAction.class, request(), IdentityContext.anonymous(),
            ActionOutcome.success(1L)));

        assertEquals(1, repository.events.size());
        assertEquals(false, evaluated.get());
    }

    @Test
    void rejectsExecutionWhenTypedActionIsNotRegistered() {
        ActionCatalog catalog = new ActionCatalog();
        catalog.freeze();
        MonitoringService service = new MonitoringService(new RecordingEventRepository(),
            new SecurityEventAssembler("demo", Clock.systemUTC()),
            new MonitoringRuntimePort() {
                public ActionDefinition resolve(Class<? extends ActionType> type) { return catalog.require(type); }
                public MonitoringRuntimePort.FactCollection collect(ActionExecution execution, ActionDefinition definition) {
                    return MonitoringRuntimePort.FactCollection.empty();
                }
            }, (type, definition, event, facts, sources, ineligible, issues) -> { }, codes());
        assertThrows(RuntimeException.class, () -> service.monitor(
            ActionExecution.of(QueryAction.class, request(), IdentityContext.anonymous(), ActionOutcome.success(1L))));
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
        MonitoringService service = new MonitoringService(new RecordingEventRepository(),
            new SecurityEventAssembler("demo", Clock.systemUTC()),
            new MonitoringRuntimePort() {
                public ActionDefinition resolve(Class<? extends ActionType> type) { return catalog.require(type); }
                public MonitoringRuntimePort.FactCollection collect(ActionExecution execution, ActionDefinition definition) {
                    return new MonitoringRuntimePort.FactCollection(facts,
                        java.util.Collections.<Class<? extends FactType<?>>, FactSource>emptyMap(),
                        java.util.Collections.<io.github.jasper.monitoring.core.domain.EventFact>emptyList());
                }
            },
            (type, definition, event, evaluatedFacts, sources, skipped, issues) -> {
                seen[0] = evaluatedFacts;
                ineligible[0] = skipped;
            }, codes());
        service.monitor(ActionExecution.of(QueryAction.class, request(), IdentityContext.anonymous(), ActionOutcome.success(1L)));
        assertSame(facts, seen[0]);
        assertTrue(ineligible[0].contains(QueryRule.class));
    }

    private static MonitoringRequestContext request() {
        return MonitoringRequestContext.builder().method("GET").path("/reports").sourceIp("127.0.0.1")
            .requestId("req-1").build();
    }

    private static StableCodeCatalog codes() {
        StableCodeCatalog catalog = new StableCodeCatalog("");
        BuiltInReasonCodes.registerInto(catalog);
        catalog.freeze();
        return catalog;
    }
    static final class QueryAction implements ActionType { }
    static final class RequiredFact implements FactType<String> { }
    static final class QueryRule implements RuleType { }

    static final class RecordingEventRepository implements EventRepository {
        private final List<SecurityEvent> events = new ArrayList<SecurityEvent>();
        @Override public void save(SecurityEvent event) { events.add(event); }
        @Override public Optional<SecurityEvent> findEvent(String eventId) {
            for (SecurityEvent event : events) {
                if (event.getEventId().equals(eventId)) return Optional.of(event);
            }
            return Optional.empty();
        }
        @Override public List<SecurityEvent> findSince(String systemId, Instant since) {
            return new ArrayList<SecurityEvent>(events);
        }
    }
}
