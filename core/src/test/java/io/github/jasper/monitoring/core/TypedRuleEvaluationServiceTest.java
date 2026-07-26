package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringMode;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionFailurePolicy;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.control.ControlCatalog;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.rule.RuleDefinition;
import io.github.jasper.monitoring.api.rule.RuleType;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import io.github.jasper.monitoring.core.application.TypedRuleEvaluationService;
import io.github.jasper.monitoring.core.application.control.ControlExecutionService;
import io.github.jasper.monitoring.core.domain.AlertDisposition;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.RuleMatch;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.WhitelistEntry;
import io.github.jasper.monitoring.core.domain.control.StoredControl;
import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import io.github.jasper.monitoring.core.domain.rule.RuleEvaluationContext;
import io.github.jasper.monitoring.core.port.AlertRepository;
import io.github.jasper.monitoring.core.port.ControlExecutionStore;
import io.github.jasper.monitoring.core.port.ControlHandler;
import io.github.jasper.monitoring.core.port.EventRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import io.github.jasper.monitoring.core.port.NotificationChannel;
import io.github.jasper.monitoring.core.port.WhitelistRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypedRuleEvaluationServiceTest {
    @Test
    void raisesAlertThroughNarrowPersistencePorts() {
        Store store = new Store();
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        ControlExecutionService controls = new ControlExecutionService(new UnusedControlStore(),
            ControlCatalog.<ControlHandler>builder().freeze(), clock);
        TypedRuleEvaluationService evaluator = new TypedRuleEvaluationService(store, store, store, store,
            Collections.<DetectionRule<? extends RuleType>>singletonList(new MatchingRule()),
            MonitoringMode.OBSERVE, controls, NotificationChannel.noop(), clock);
        ActionDefinition action = ActionDefinition.builder("data:query").eventType(SecurityEventType.QUERY)
            .resourceType("report").failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
        MonitoringService service = new MonitoringService(store, new SecurityEventAssembler("demo", clock),
            new FixedRuntime(action), evaluator);

        service.monitor(ActionExecution.of(QueryAction.class, request(), IdentityContext.anonymous(),
            ActionOutcome.success(1L)));

        assertEquals(1, store.alerts.size());
        assertEquals("QUERY-01", store.alerts.get(0).getRuleId());
    }

    private static MonitoringRequestContext request() {
        return MonitoringRequestContext.builder().method("GET").path("/reports").sourceIp("127.0.0.1")
            .requestId("req-1").build();
    }

    static final class QueryAction implements ActionType { }
    static final class QueryRule implements RuleType { }
    static final class MatchingRule implements DetectionRule<QueryRule> {
        private final RuleDefinition<QueryRule> definition = RuleDefinition.builder(QueryRule.class, "QUERY-01")
            .appliesTo(QueryAction.class).historyWindow(Duration.ofMinutes(5)).threshold(1L)
            .risk(RiskLevel.HIGH).mode(io.github.jasper.monitoring.api.rule.RuleMode.OBSERVE)
            .source(io.github.jasper.monitoring.api.rule.RuleSource.INTERNAL)
            .control(ControlActionType.RECORD).build();
        @Override public RuleDefinition<QueryRule> definition() { return definition; }
        @Override public Optional<RuleMatch> evaluate(RuleEvaluationContext context) {
            return Optional.of(new RuleMatch("QUERY-01", RiskLevel.HIGH, context.getEvent().subject(),
                "reports", "query observed", Collections.singletonList(ControlActionType.RECORD)));
        }
    }

    static final class FixedRuntime implements io.github.jasper.monitoring.core.application.MonitoringRuntimePort {
        private final ActionDefinition action;
        FixedRuntime(ActionDefinition action) { this.action = action; }
        @Override public ActionDefinition resolve(Class<? extends ActionType> type) { return action; }
        @Override public ActionFacts collect(ActionExecution execution, ActionDefinition definition) {
            return ActionFacts.builder().build();
        }
    }

    static final class Store implements EventRepository, AlertRepository, WhitelistRepository, MonitoringTransaction {
        private final List<SecurityEvent> events = new ArrayList<SecurityEvent>();
        private final List<SecurityAlert> alerts = new ArrayList<SecurityAlert>();
        @Override public void save(SecurityEvent event) { events.add(event); }
        @Override public Optional<SecurityEvent> findEvent(String id) { return Optional.empty(); }
        @Override public List<SecurityEvent> findSince(String systemId, Instant since) { return new ArrayList<SecurityEvent>(events); }
        @Override public void save(SecurityAlert alert) { alerts.add(alert); }
        @Override public Optional<SecurityAlert> findAlert(String id) { return Optional.empty(); }
        @Override public Optional<SecurityAlert> findOpen(String fingerprint) { return Optional.empty(); }
        @Override public void linkEvent(String alertId, String eventId) { }
        @Override public void appendDisposition(AlertDisposition disposition) { }
        @Override public List<AlertDisposition> findDispositions(String alertId) { return Collections.emptyList(); }
        @Override public boolean isActive(String ruleId, String subject, Instant at) { return false; }
        @Override public void add(WhitelistEntry entry) { }
        @Override public <T> T required(io.github.jasper.monitoring.core.port.TransactionWork<T> work) { return work.execute(); }
    }

    static final class UnusedControlStore implements ControlExecutionStore {
        @Override public Optional<StoredControl> find(String key) { return Optional.empty(); }
        @Override public boolean reserve(ControlCommand command, io.github.jasper.monitoring.api.control.ControlStatus status,
                                         Instant at) { throw new AssertionError("control must not run in OBSERVE"); }
        @Override public StoredControl transition(String key, long version,
                io.github.jasper.monitoring.api.control.ControlStatus expected,
                io.github.jasper.monitoring.api.control.ControlStatus target, String reason, Instant at) {
            throw new AssertionError("control must not run in OBSERVE");
        }
    }
}
