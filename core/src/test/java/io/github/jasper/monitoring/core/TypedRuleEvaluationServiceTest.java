package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringMode;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.action.ActionDisposition;
import io.github.jasper.monitoring.api.action.ActionFailurePolicy;
import io.github.jasper.monitoring.api.action.ActionRequirement;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.api.code.StableCodeCatalog;
import io.github.jasper.monitoring.api.control.ControlCatalog;
import io.github.jasper.monitoring.api.control.ControlStatus;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.rule.RuleDefinition;
import io.github.jasper.monitoring.api.rule.RuleMode;
import io.github.jasper.monitoring.api.rule.RuleSource;
import io.github.jasper.monitoring.api.rule.RuleType;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import io.github.jasper.monitoring.core.application.TypedRuleEvaluationService;
import io.github.jasper.monitoring.core.application.control.ControlExecutionService;
import io.github.jasper.monitoring.core.application.notification.NotificationDeliveryService;
import io.github.jasper.monitoring.core.domain.AlertDisposition;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.RuleMatch;
import io.github.jasper.monitoring.core.domain.NotificationDelivery;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.WhitelistEntry;
import io.github.jasper.monitoring.core.domain.control.StoredControl;
import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import io.github.jasper.monitoring.core.domain.rule.RuleObservation;
import io.github.jasper.monitoring.core.domain.rule.RuleEvaluationContext;
import io.github.jasper.monitoring.core.port.AlertRepository;
import io.github.jasper.monitoring.core.port.ControlExecutionStore;
import io.github.jasper.monitoring.core.port.ControlHandler;
import io.github.jasper.monitoring.core.port.EventRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import io.github.jasper.monitoring.core.port.NotificationChannel;
import io.github.jasper.monitoring.core.port.NotificationDeliveryRepository;
import io.github.jasper.monitoring.core.port.RuleObservationRepository;
import io.github.jasper.monitoring.core.port.WhitelistRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedRuleEvaluationServiceTest {
    @Test
    void synchronousDecisionAggregatesRuleOutputsWithoutCreatingSideEffects() {
        Store store = new Store();
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        RecordingControlStore controlStore = new RecordingControlStore();
        TypedRuleEvaluationService evaluator = new TypedRuleEvaluationService(store, store, store, store, store,
            Collections.<DetectionRule<? extends RuleType>>singletonList(new BlockingRule()),
            MonitoringMode.ENFORCE,
            new ControlExecutionService(controlStore, ControlCatalog.<ControlHandler>builder().freeze(), clock),
            notifications(store, NotificationChannel.noop(), clock), clock);
        ActionDefinition action = ActionDefinition.builder("data:query")
            .eventType(SecurityEventType.QUERY).resourceType("report")
            .failurePolicy(ActionFailurePolicy.FAIL_CLOSED).build();
        MonitoringService service = new MonitoringService(store, new SecurityEventAssembler("demo", clock),
            new FixedRuntime(action), evaluator, codes());

        ActionDecision decision = service.decide(ActionExecution.of(QueryAction.class, request(),
            IdentityContext.anonymous(), ActionOutcome.success(0L)));

        assertEquals(ActionDisposition.BLOCK, decision.getDisposition());
        assertTrue(decision.getRequirements().contains(ActionRequirement.APPROVAL));
        assertTrue(decision.getMatchedRuleIds().contains("BLOCK-01"));
        assertEquals(0, store.events.size());
        assertEquals(0, store.alerts.size());
        assertEquals(0, store.observations.size());
        assertEquals(0, controlStore.reservations);
    }
    @Test
    void disabledRuleIsNotEvaluated() {
        ModeFixture fixture = new ModeFixture(RuleMode.DISABLED, MonitoringMode.ENFORCE);

        fixture.monitor();

        fixture.assertEffects(0, 0, 0, 0, 0);
    }

    @Test
    void observeRuleSavesOneObservationWithoutAlertNotificationOrControl() {
        ModeFixture fixture = new ModeFixture(RuleMode.OBSERVE, MonitoringMode.ENFORCE);

        fixture.monitor();

        fixture.assertEffects(1, 1, 0, 0, 0);
        RuleObservation observation = fixture.store.observations.get(0);
        assertNotNull(observation.getObservationId());
        assertEquals("MODE-01", observation.getRuleId());
        assertEquals(fixture.store.events.get(0).getEventId(), observation.getEventId());
        assertEquals(fixture.store.events.get(0).subject(), observation.getSubject());
        assertEquals(Instant.EPOCH, observation.getObservedAt());
    }

    @Test
    void alertOnlyRuleAlertsAndNotifiesWithoutControlInGlobalEnforceMode() {
        ModeFixture fixture = new ModeFixture(RuleMode.ALERT_ONLY, MonitoringMode.ENFORCE);

        fixture.monitor();

        fixture.assertEffects(1, 0, 1, 1, 0);
    }

    @Test
    void enforceRuleAlertsNotifiesAndControlsInGlobalEnforceMode() {
        ModeFixture fixture = new ModeFixture(RuleMode.ENFORCE, MonitoringMode.ENFORCE);

        fixture.monitor();

        fixture.assertEffects(1, 0, 1, 1, 1);
    }

    @Test
    void globalObserveModePreventsControlForEnforceRuleWithoutSuppressingAlert() {
        ModeFixture fixture = new ModeFixture(RuleMode.ENFORCE, MonitoringMode.OBSERVE);

        fixture.monitor();

        fixture.assertEffects(1, 0, 1, 1, 0);
    }

    @Test
    void ruleObservationRequiresEveryField() {
        assertThrows(NullPointerException.class,
            () -> RuleObservation.of(null, "rule", "event", "subject", Instant.EPOCH));
        assertThrows(NullPointerException.class,
            () -> RuleObservation.of("observation", null, "event", "subject", Instant.EPOCH));
        assertThrows(NullPointerException.class,
            () -> RuleObservation.of("observation", "rule", null, "subject", Instant.EPOCH));
        assertThrows(NullPointerException.class,
            () -> RuleObservation.of("observation", "rule", "event", null, Instant.EPOCH));
        assertThrows(NullPointerException.class,
            () -> RuleObservation.of("observation", "rule", "event", "subject", null));
        assertThrows(IllegalArgumentException.class,
            () -> RuleObservation.of(" ", "rule", "event", "subject", Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
            () -> RuleObservation.of("observation", " ", "event", "subject", Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
            () -> RuleObservation.of("observation", "rule", " ", "subject", Instant.EPOCH));
        assertThrows(IllegalArgumentException.class,
            () -> RuleObservation.of("observation", "rule", "event", " ", Instant.EPOCH));
    }

    @Test
    void raisesAlertThroughNarrowPersistencePorts() {
        Store store = new Store();
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        ControlExecutionService controls = new ControlExecutionService(new UnusedControlStore(),
            ControlCatalog.<ControlHandler>builder().freeze(), clock);
        TypedRuleEvaluationService evaluator = new TypedRuleEvaluationService(store, store, store, store, store,
            Collections.<DetectionRule<? extends RuleType>>singletonList(new MatchingRule()),
            MonitoringMode.OBSERVE, controls, notifications(store, NotificationChannel.noop(), clock), clock);
        ActionDefinition action = ActionDefinition.builder("data:query").eventType(SecurityEventType.QUERY)
            .resourceType("report").failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
        MonitoringService service = new MonitoringService(store, new SecurityEventAssembler("demo", clock),
            new FixedRuntime(action), evaluator, codes());

        service.monitor(ActionExecution.of(QueryAction.class, request(), IdentityContext.anonymous(),
            ActionOutcome.success(1L)));

        assertEquals(1, store.alerts.size());
        assertEquals("QUERY-01", store.alerts.get(0).getRuleId());
    }

    @Test
    void deliversNotificationOnlyAfterTheAlertTransactionCompletes() {
        TrackingStore store = new TrackingStore();
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        boolean[] notified = new boolean[1];
        boolean[] registered = new boolean[1];
        TestNotificationDeliveries deliveryStore = new TestNotificationDeliveries() {
            @Override public boolean create(NotificationDelivery delivery) {
                assertEquals(true, store.inTransaction);
                registered[0] = true;
                return super.create(delivery);
            }
        };
        NotificationDeliveryService notifications = new NotificationDeliveryService("test", (deliveryId, alert) -> {
            assertEquals(false, store.inTransaction);
            notified[0] = true;
        }, deliveryStore, store, clock, 3, Duration.ofMinutes(1), Duration.ofMinutes(5));

        monitorMatchingRule(store, notifications, clock);

        assertEquals(1, store.alertCount());
        assertEquals(true, registered[0]);
        assertEquals(true, notified[0]);
    }

    @Test
    void keepsTheCommittedAlertWhenTheExternalChannelFails() {
        TrackingStore store = new TrackingStore();
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        NotificationDeliveryService notifications = notifications(store, (deliveryId, alert) -> {
            throw new IllegalStateException("provider unavailable");
        }, clock);

        monitorMatchingRule(store, notifications, clock);

        assertEquals(1, store.alertCount());
        assertEquals(false, store.inTransaction);
    }

    @Test
    void preservesClientSupplementalFactSourceForRuleEligibility() {
        Store store = new Store();
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        TypedRuleEvaluationService evaluator = evaluator(store, new SourceAwareRule(), clock);
        ActionDefinition action = ActionDefinition.builder("client:signal")
            .eventType(SecurityEventType.QUERY).resourceType("signal")
            .require(ClientFact.class, FactSource.CLIENT_SUPPLEMENTAL)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
        MonitoringService service = new MonitoringService(store,
            new SecurityEventAssembler("demo", clock), new FixedRuntime(action), evaluator, codes());

        service.monitor(ActionExecution.of(QueryAction.class, request(), IdentityContext.anonymous(),
            ActionOutcome.success(1L), ActionFacts.builder().put(ClientFact.class, "observed").build(),
            FactSource.CLIENT_SUPPLEMENTAL));

        assertEquals(1, store.alerts.size());
    }

    @Test
    void evaluatesWithCanonicalCurrentEventWhenDatabaseRoundTripOmitsIt() {
        Store store = new EmptyHistoryStore();
        Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        TypedRuleEvaluationService evaluator = evaluator(store, new CurrentEventRule(), clock);
        ActionDefinition action = ActionDefinition.builder("data:query").eventType(SecurityEventType.QUERY)
            .resourceType("report").failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
        MonitoringService service = new MonitoringService(store,
            new SecurityEventAssembler("demo", clock), new FixedRuntime(action), evaluator, codes());

        service.monitor(ActionExecution.of(QueryAction.class, request(), IdentityContext.anonymous(),
            ActionOutcome.success(1L)));

        assertEquals(1, store.alerts.size());
    }

    private static TypedRuleEvaluationService evaluator(Store store,
            DetectionRule<? extends RuleType> rule, Clock clock) {
        ControlExecutionService controls = new ControlExecutionService(new UnusedControlStore(),
            ControlCatalog.<ControlHandler>builder().freeze(), clock);
        return new TypedRuleEvaluationService(store, store, store, store, store,
            Collections.<DetectionRule<? extends RuleType>>singletonList(rule),
            MonitoringMode.OBSERVE, controls, notifications(store, NotificationChannel.noop(), clock), clock);
    }

    private static void monitorMatchingRule(Store store, NotificationDeliveryService notifications, Clock clock) {
        ControlExecutionService controls = new ControlExecutionService(new UnusedControlStore(),
            ControlCatalog.<ControlHandler>builder().freeze(), clock);
        TypedRuleEvaluationService evaluator = new TypedRuleEvaluationService(store, store, store, store, store,
            Collections.<DetectionRule<? extends RuleType>>singletonList(new MatchingRule()),
            MonitoringMode.OBSERVE, controls, notifications, clock);
        ActionDefinition action = ActionDefinition.builder("data:query").eventType(SecurityEventType.QUERY)
            .resourceType("report").failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
        MonitoringService service = new MonitoringService(store, new SecurityEventAssembler("demo", clock),
            new FixedRuntime(action), evaluator, codes());
        service.monitor(ActionExecution.of(QueryAction.class, request(), IdentityContext.anonymous(),
            ActionOutcome.success(1L)));
    }

    private static MonitoringRequestContext request() {
        return MonitoringRequestContext.builder().method("GET").path("/reports").sourceIp("127.0.0.1")
            .requestId("req-1").build();
    }

    static final class QueryAction implements ActionType { }
    static final class QueryRule implements RuleType { }
    static final class ClientFact implements FactType<String> { }
    static final class MatchingRule implements DetectionRule<QueryRule> {
        private final RuleDefinition<QueryRule> definition = RuleDefinition.builder(QueryRule.class, "QUERY-01")
            .appliesTo(QueryAction.class).historyWindow(Duration.ofMinutes(5)).threshold(1L)
            .risk(RiskLevel.HIGH).mode(RuleMode.ALERT_ONLY)
            .source(io.github.jasper.monitoring.api.rule.RuleSource.INTERNAL)
            .control(ControlActionType.RECORD).build();
        @Override public RuleDefinition<QueryRule> definition() { return definition; }
        @Override public Optional<RuleMatch> evaluate(RuleEvaluationContext context) {
            return Optional.of(new RuleMatch("QUERY-01", RiskLevel.HIGH, context.getEvent().subject(),
                "reports", "query observed", ActionDisposition.ALLOW,
                Collections.<ActionRequirement>emptySet(),
                Collections.singleton(ControlActionType.RECORD), Duration.ofMinutes(15)));
        }
    }

    static final class SourceAwareRule implements DetectionRule<QueryRule> {
        private final RuleDefinition<QueryRule> definition = RuleDefinition.builder(QueryRule.class, "SOURCE-01")
            .appliesTo(QueryAction.class).require(ClientFact.class, FactSource.CLIENT_SUPPLEMENTAL)
            .historyWindow(Duration.ZERO).threshold(1L).risk(RiskLevel.MEDIUM)
            .mode(RuleMode.ALERT_ONLY)
            .source(io.github.jasper.monitoring.api.rule.RuleSource.INTERNAL)
            .control(ControlActionType.RECORD).build();
        @Override public RuleDefinition<QueryRule> definition() { return definition; }
        @Override public Optional<RuleMatch> evaluate(RuleEvaluationContext context) {
            assertEquals(FactSource.CLIENT_SUPPLEMENTAL, context.getFactSource(ClientFact.class));
            return Optional.of(match("SOURCE-01", context));
        }
    }

    static final class CurrentEventRule implements DetectionRule<QueryRule> {
        private final RuleDefinition<QueryRule> definition = RuleDefinition.builder(QueryRule.class, "CURRENT-01")
            .appliesTo(QueryAction.class).historyWindow(Duration.ofMinutes(1)).threshold(1L)
            .risk(RiskLevel.MEDIUM).mode(RuleMode.ALERT_ONLY)
            .source(io.github.jasper.monitoring.api.rule.RuleSource.INTERNAL)
            .control(ControlActionType.RECORD).build();
        @Override public RuleDefinition<QueryRule> definition() { return definition; }
        @Override public Optional<RuleMatch> evaluate(RuleEvaluationContext context) {
            for (SecurityEvent candidate : context.getHistory()) {
                if (candidate == context.getEvent()) return Optional.of(match("CURRENT-01", context));
            }
            return Optional.empty();
        }
    }

    private static RuleMatch match(String ruleId, RuleEvaluationContext context) {
        return new RuleMatch(ruleId, RiskLevel.MEDIUM, context.getEvent().subject(), "reports",
            "observed", ActionDisposition.ALLOW, Collections.<ActionRequirement>emptySet(),
            Collections.singleton(ControlActionType.RECORD), Duration.ofMinutes(15));
    }

    static final class ModeFixture {
        private final Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        private final Store store = new Store();
        private final ModeRule rule;
        private final RecordingControlStore controlStore = new RecordingControlStore();
        private final RecordingNotifications notifications = new RecordingNotifications();
        private final MonitoringService service;

        ModeFixture(RuleMode ruleMode, MonitoringMode monitoringMode) {
            rule = new ModeRule(ruleMode);
            ControlExecutionService controls = new ControlExecutionService(controlStore,
                ControlCatalog.<ControlHandler>builder().freeze(), clock);
            TypedRuleEvaluationService evaluator = new TypedRuleEvaluationService(store, store, store, store, store,
                Collections.<DetectionRule<? extends RuleType>>singletonList(rule), monitoringMode,
                controls, notifications(store, notifications, clock), clock);
            ActionDefinition action = ActionDefinition.builder("data:query")
                .eventType(SecurityEventType.QUERY).resourceType("report")
                .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
            service = new MonitoringService(store, new SecurityEventAssembler("demo", clock),
                new FixedRuntime(action), evaluator, codes());
        }

        void monitor() {
            service.monitor(ActionExecution.of(QueryAction.class, request(), IdentityContext.anonymous(),
                ActionOutcome.success(1L)));
        }

        void assertEffects(int evaluations, int observations, int alerts, int notificationCount, int controls) {
            assertEquals(evaluations, rule.evaluations);
            assertEquals(observations, store.observations.size());
            assertEquals(alerts, store.alerts.size());
            assertEquals(notificationCount, notifications.alerts.size());
            assertEquals(controls, controlStore.reservations);
        }
    }

    static final class ModeRule implements DetectionRule<QueryRule> {
        private final RuleDefinition<QueryRule> definition;
        private int evaluations;

        ModeRule(RuleMode mode) {
            definition = RuleDefinition.builder(QueryRule.class, "MODE-01")
                .appliesTo(QueryAction.class).historyWindow(Duration.ZERO).threshold(1L)
                .risk(RiskLevel.HIGH).mode(mode).source(RuleSource.INTERNAL)
                .control(ControlActionType.REQUIRE_APPROVAL).build();
        }

        @Override public RuleDefinition<QueryRule> definition() { return definition; }
        @Override public Optional<RuleMatch> evaluate(RuleEvaluationContext context) {
            evaluations++;
            return Optional.of(new RuleMatch("MODE-01", RiskLevel.HIGH, context.getEvent().subject(),
                "reports", "query observed", ActionDisposition.ALLOW,
                Collections.<ActionRequirement>emptySet(),
                Collections.singleton(ControlActionType.REQUIRE_APPROVAL), Duration.ofMinutes(15)));
        }
    }

    static final class BlockingRule implements DetectionRule<QueryRule> {
        private final RuleDefinition<QueryRule> definition = RuleDefinition.builder(QueryRule.class, "BLOCK-01")
            .appliesTo(QueryAction.class).historyWindow(Duration.ZERO).threshold(1L)
            .risk(RiskLevel.HIGH).disposition(ActionDisposition.BLOCK)
            .requirement(ActionRequirement.APPROVAL)
            .mode(RuleMode.ENFORCE).source(RuleSource.INTERNAL).build();

        @Override public RuleDefinition<QueryRule> definition() { return definition; }
        @Override public Optional<RuleMatch> evaluate(RuleEvaluationContext context) {
            return Optional.of(new RuleMatch("BLOCK-01", RiskLevel.HIGH,
                context.getEvent().subject(), "reports", "approval required",
                ActionDisposition.BLOCK, Collections.singleton(ActionRequirement.APPROVAL),
                Collections.<ControlActionType>emptySet(), Duration.ofMinutes(15)));
        }
    }

    static final class RecordingNotifications implements NotificationChannel {
        private final List<SecurityAlert> alerts = new ArrayList<SecurityAlert>();
        @Override public void notify(String deliveryId, SecurityAlert alert) { alerts.add(alert); }
    }

    private static NotificationDeliveryService notifications(Store store, NotificationChannel channel, Clock clock) {
        return new NotificationDeliveryService("test", channel, new TestNotificationDeliveries(), store, clock,
            3, Duration.ofMinutes(1), Duration.ofMinutes(5));
    }

    static class TestNotificationDeliveries implements NotificationDeliveryRepository {
        private final Map<String, NotificationDelivery> values =
            new LinkedHashMap<String, NotificationDelivery>();
        @Override public Optional<NotificationDelivery> find(String channel, String aggregateId) {
            for (NotificationDelivery value : values.values()) {
                if (value.getChannel().equals(channel) && value.getAggregateId().equals(aggregateId)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }
        @Override public boolean create(NotificationDelivery delivery) {
            return values.put(delivery.getDeliveryId(), delivery) == null;
        }
        @Override public boolean update(NotificationDelivery delivery, long expectedVersion) {
            NotificationDelivery current = values.get(delivery.getDeliveryId());
            if (current == null || current.getVersion() != expectedVersion) return false;
            values.put(delivery.getDeliveryId(), delivery);
            return true;
        }
            @Override public List<NotificationDelivery> findDue(String channel, Instant at, int limit) {
            List<NotificationDelivery> result = new ArrayList<NotificationDelivery>();
            for (NotificationDelivery value : values.values()) {
                if (result.size() == limit) break;
                if (value.isDueAt(channel, at)) result.add(value);
            }
            return result;
        }
    }

    static final class RecordingControlStore implements ControlExecutionStore {
        private int reservations;
        @Override public Optional<StoredControl> find(String key) { return Optional.empty(); }
        @Override public boolean reserve(ControlCommand command, ControlStatus status, Instant at) {
            reservations++;
            return true;
        }
        @Override public StoredControl transition(String key, long version, ControlStatus expected,
                ControlStatus target, String reason, Instant at) {
            throw new AssertionError("approval controls do not transition during evaluation");
        }
    }

    private static StableCodeCatalog codes() {
        StableCodeCatalog catalog = new StableCodeCatalog("");
        BuiltInReasonCodes.registerInto(catalog);
        catalog.freeze();
        return catalog;
    }

    static final class FixedRuntime implements io.github.jasper.monitoring.core.application.MonitoringRuntimePort {
        private final ActionDefinition action;
        FixedRuntime(ActionDefinition action) { this.action = action; }
        @Override public ActionDefinition resolve(Class<? extends ActionType> type) { return action; }
        @Override public FactCollection collect(ActionExecution execution, ActionDefinition definition) {
            java.util.Map<Class<? extends FactType<?>>, FactSource> sources =
                new java.util.LinkedHashMap<Class<? extends FactType<?>>, FactSource>();
            for (Class<? extends FactType<?>> type : execution.getSuppliedFacts().asMap().keySet()) {
                sources.put(type, execution.getSuppliedFactSource());
            }
            java.util.List<io.github.jasper.monitoring.core.domain.EventFact> persisted =
                new java.util.ArrayList<io.github.jasper.monitoring.core.domain.EventFact>();
            for (Class<? extends FactType<?>> type : execution.getSuppliedFacts().asMap().keySet()) {
                Object value = execution.getSuppliedFacts().asMap().get(type);
                persisted.add(new io.github.jasper.monitoring.core.domain.EventFact(type.getSimpleName(),
                    value.getClass().getName(), String.valueOf(value), execution.getSuppliedFactSource()));
            }
            return new FactCollection(execution.getSuppliedFacts(), sources, persisted);
        }
    }

    static class Store implements EventRepository, AlertRepository, WhitelistRepository, MonitoringTransaction,
            RuleObservationRepository {
        private final List<SecurityEvent> events = new ArrayList<SecurityEvent>();
        private final List<SecurityAlert> alerts = new ArrayList<SecurityAlert>();
        private final List<RuleObservation> observations = new ArrayList<RuleObservation>();
        @Override public void save(SecurityEvent event) { events.add(event); }
        @Override public Optional<SecurityEvent> findEvent(String id) { return Optional.empty(); }
        @Override public List<SecurityEvent> findSince(String systemId, Instant since) { return new ArrayList<SecurityEvent>(events); }
        @Override public void save(SecurityAlert alert) { alerts.add(alert); }
        int alertCount() { return alerts.size(); }
        @Override public Optional<SecurityAlert> findAlert(String id) { return Optional.empty(); }
        @Override public Optional<SecurityAlert> findOpen(String fingerprint) { return Optional.empty(); }
        @Override public void linkEvent(String alertId, String eventId) { }
        @Override public void appendDisposition(AlertDisposition disposition) { }
        @Override public List<AlertDisposition> findDispositions(String alertId) { return Collections.emptyList(); }
        @Override public void save(RuleObservation observation) { observations.add(observation); }
        @Override public boolean isActive(String ruleId, String subject, Instant at) { return false; }
        @Override public void add(WhitelistEntry entry) { }
        @Override public <T> T required(io.github.jasper.monitoring.core.port.TransactionWork<T> work) { return work.execute(); }
    }

    static final class TrackingStore extends Store {
        private boolean inTransaction;
        @Override public <T> T required(io.github.jasper.monitoring.core.port.TransactionWork<T> work) {
            inTransaction = true;
            try {
                return work.execute();
            } finally {
                inTransaction = false;
            }
        }
    }

    static final class EmptyHistoryStore extends Store {
        @Override public List<SecurityEvent> findSince(String systemId, Instant since) {
            return Collections.emptyList();
        }
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
