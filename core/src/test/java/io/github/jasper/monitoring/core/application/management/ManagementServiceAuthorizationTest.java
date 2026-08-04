package io.github.jasper.monitoring.core.application.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.jasper.monitoring.api.error.ManagementAccessDeniedException;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementOperation;
import io.github.jasper.monitoring.api.management.ManagementAuthorizer;
import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.ManagementPageRequest;
import io.github.jasper.monitoring.api.management.command.AlertAcknowledgeCommand;
import io.github.jasper.monitoring.api.management.command.AlertAssignmentCommand;
import io.github.jasper.monitoring.api.management.command.RuleChangeCommand;
import io.github.jasper.monitoring.api.management.command.ControlApprovalCommand;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.control.ControlCatalog;
import io.github.jasper.monitoring.api.rule.RuleMode;
import io.github.jasper.monitoring.api.error.ManagementConflictException;
import io.github.jasper.monitoring.api.management.model.AlertView;
import io.github.jasper.monitoring.api.management.model.AlertAssignmentView;
import io.github.jasper.monitoring.api.management.model.ControlView;
import io.github.jasper.monitoring.api.management.model.RuleView;
import io.github.jasper.monitoring.api.management.model.SecurityEventView;
import io.github.jasper.monitoring.api.management.model.WhitelistView;
import io.github.jasper.monitoring.api.management.query.AlertQuery;
import io.github.jasper.monitoring.api.management.query.AlertAssignmentQuery;
import io.github.jasper.monitoring.api.management.query.ControlQuery;
import io.github.jasper.monitoring.api.management.query.RuleQuery;
import io.github.jasper.monitoring.api.management.query.SecurityEventQuery;
import io.github.jasper.monitoring.api.management.query.WhitelistQuery;
import io.github.jasper.monitoring.core.domain.management.ManagementAuditRecord;
import io.github.jasper.monitoring.core.port.ManagementAuditRepository;
import io.github.jasper.monitoring.core.port.ManagementQueryRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import io.github.jasper.monitoring.core.port.ControlExecutionStore;
import io.github.jasper.monitoring.core.port.WhitelistRepository;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.WhitelistEntry;
import io.github.jasper.monitoring.core.application.control.ControlExecutionService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ManagementServiceAuthorizationTest {
    private final ManagementActor actor = ManagementActor.of("operator-1", "system-a");
    private final ManagementActor approver = ManagementActor.of("approver-1", "system-a");
    private final RecordingAudits audits = new RecordingAudits();
    private final FakeQueries queries = new FakeQueries();
    private final MonitoringTransaction transaction = new MonitoringTransaction() {
        @Override public <T> T required(io.github.jasper.monitoring.core.port.TransactionWork<T> work) {
            return work.execute();
        }
    };
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-26T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void deniedSearchDoesNotReadPersistenceAndWritesDeniedAudit() {
        ManagementAuthorizer denied = (principal, operation, resource) -> {
            throw new ManagementAccessDeniedException("denied");
        };
        DefaultSecurityEventQueryService service = new DefaultSecurityEventQueryService(
            new ManagementAccessGuard(denied, audits, clock), queries, transaction);
        SecurityEventQuery query = SecurityEventQuery.of(
            ManagementPageRequest.of(0, 20, SecurityEventQuery.Sort.OCCURRED_AT),
            Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-26T00:00:00Z"));

        assertThrows(ManagementAccessDeniedException.class, () -> service.search(actor, query));

        assertEquals(0, queries.reads);
        assertEquals(ManagementAuditRecord.Outcome.DENIED, audits.records.get(0).getOutcome());
    }

    @Test
    void authorizedMutationUsesExpectedVersionAndAuditsSuccess() {
        DefaultAlertManagementService service = new DefaultAlertManagementService(
            new ManagementAccessGuard((principal, operation, resource) -> { }, audits, clock), queries, transaction);

        AlertView result = service.acknowledge(actor,
            AlertAcknowledgeCommand.of("alert-1", 1, "accepted", "request-1"));

        assertEquals("alert-1", result.getId());
        assertEquals(1, queries.transitions);
        assertEquals(ManagementAuditRecord.Outcome.SUCCEEDED, audits.records.get(0).getOutcome());
    }

    @Test
    void ruleChangeAppendsVersionAndAuditsSuccess() {
        DefaultRuleCatalogService service = new DefaultRuleCatalogService(
            new ManagementAccessGuard((principal, operation, resource) -> { }, audits, clock), queries, transaction);

        RuleView result = service.change(actor, approver, RuleChangeCommand.of("rule-1", 1, RuleMode.ENFORCE, 5,
            "approved adjustment", "rule-change-1"));

        assertEquals(2, result.getVersion());
        assertEquals(RuleMode.ENFORCE, result.getMode());
        assertEquals(ManagementOperation.RULE_APPROVE, audits.records.get(0).getOperation());
        assertEquals("approver-1", audits.records.get(0).getActorId());
        assertEquals(ManagementOperation.RULE_CHANGE, audits.records.get(1).getOperation());
    }

    @Test
    void staleRuleChangeIsRejectedWithoutSuccessAudit() {
        DefaultRuleCatalogService service = new DefaultRuleCatalogService(
            new ManagementAccessGuard((principal, operation, resource) -> { }, audits, clock), queries, transaction);

        assertThrows(ManagementConflictException.class, () -> service.change(actor, approver,
            RuleChangeCommand.of("rule-1", 3, RuleMode.ALERT_ONLY, 5, "stale", "stale-3")));

        assertTrue(audits.records.isEmpty());
    }

    @Test
    void alertAssignmentUsesDedicatedPortAndAdvancesVersion() {
        DefaultAlertManagementService service = new DefaultAlertManagementService(
            new ManagementAccessGuard((principal, operation, resource) -> { }, audits, clock), queries, transaction);

        AlertView result = service.assign(actor,
            AlertAssignmentCommand.of("alert-1", 1, "analyst-1", "triage", "assign-1"));

        assertEquals(2, result.getVersion());
        assertEquals(1, queries.assignments);
        assertEquals(ManagementOperation.ALERT_ASSIGN, audits.records.get(0).getOperation());
    }

    @Test
    void newlyCreatedVersionZeroAlertCanBeAssigned() {
        DefaultAlertManagementService service = new DefaultAlertManagementService(
            new ManagementAccessGuard((principal, operation, resource) -> { }, audits, clock), queries, transaction);

        service.assign(actor, AlertAssignmentCommand.of("alert-1", 0, "analyst-1", "initial triage", "assign-0"));

        assertEquals(1, queries.assignmentWrites);
    }

    @Test
    void ruleChangeRequiresDistinctAuthorizedApprover() {
        DefaultRuleCatalogService service = new DefaultRuleCatalogService(
            new ManagementAccessGuard((principal, operation, resource) -> { }, audits, clock), queries, transaction);

        assertThrows(ManagementAccessDeniedException.class, () -> service.change(actor, actor,
            RuleChangeCommand.of("rule-1", 1, RuleMode.ENFORCE, 5, "self approved", "self-1")));

        assertEquals(ManagementAuditRecord.Outcome.DENIED, audits.records.get(0).getOutcome());
        assertEquals(1, queries.rule.getVersion());
    }

    @Test
    void successfulManagementWritesCanBeReplayedWithoutRepeatingSideEffects() {
        DefaultRuleCatalogService rules = new DefaultRuleCatalogService(
            new ManagementAccessGuard((principal, operation, resource) -> { }, audits, clock), queries, transaction);
        RuleChangeCommand ruleCommand = RuleChangeCommand.of("rule-1", 1, RuleMode.ENFORCE, 5,
            "approved adjustment", "rule-replay-1");
        rules.change(actor, approver, ruleCommand);
        RuleView replayedRule = rules.change(actor, approver, ruleCommand);

        DefaultAlertManagementService alerts = new DefaultAlertManagementService(
            new ManagementAccessGuard((principal, operation, resource) -> { }, audits, clock), queries, transaction);
        AlertAssignmentCommand assignment = AlertAssignmentCommand.of("alert-1", 1, "analyst-1", "triage",
            "assignment-replay-1");
        alerts.assign(actor, assignment);
        AlertView replayedAssignment = alerts.assign(actor, assignment);

        assertEquals(2, replayedRule.getVersion());
        assertEquals(2, replayedAssignment.getVersion());
        assertEquals(1, queries.assignmentWrites);
    }

    @Test
    void expiredPassIsRejectedBeforeTheControlExecutionStoreIsRead() {
        AtomicInteger executionReads = new AtomicInteger();
        ControlExecutionStore controlStore = new ControlExecutionStore() {
            @Override public Optional<io.github.jasper.monitoring.core.domain.control.StoredControl> find(String key) {
                executionReads.incrementAndGet();
                return Optional.empty();
            }
            @Override public boolean reserve(ControlCommand command,
                    io.github.jasper.monitoring.api.control.ControlStatus status, Instant at) { return false; }
            @Override public io.github.jasper.monitoring.core.domain.control.StoredControl transition(String key,
                    long version, io.github.jasper.monitoring.api.control.ControlStatus expected,
                    io.github.jasper.monitoring.api.control.ControlStatus target, String reason, Instant at) {
                throw new AssertionError("control must not execute");
            }
        };
        WhitelistRepository passes = new WhitelistRepository() {
            @Override public boolean isActive(String systemId, String ruleId, String subject, Instant at) {
                return false;
            }
            @Override public void add(WhitelistEntry entry) { throw new AssertionError("pass must not persist"); }
        };
        DefaultControlManagementService service = new DefaultControlManagementService(
            new ManagementAccessGuard((principal, operation, resource) -> { }, audits, clock), queries, transaction,
            new ControlExecutionService(controlStore,
                ControlCatalog.<io.github.jasper.monitoring.core.port.ControlHandler>builder().freeze(), clock),
            passes, clock);

        assertThrows(IllegalArgumentException.class, () -> service.approve(actor,
            ControlApprovalCommand.withPassUntil("control-1", 1, "expired", Instant.now(clock))));

        assertEquals(0, executionReads.get());
    }

    private static final class RecordingAudits implements ManagementAuditRepository {
        private final List<ManagementAuditRecord> records = new ArrayList<ManagementAuditRecord>();
        @Override public void append(ManagementAuditRecord record) { records.add(record); }
    }

    private static final class FakeQueries implements ManagementQueryRepository {
        private int reads;
        private int transitions;
        private int assignments;
        private int assignmentWrites;
        private RuleView rule = RuleView.of("rule-1", "system-a", 1, RuleMode.ALERT_ONLY, 1);
        private String ruleKey;
        private String assignmentKey;
        @Override public ManagementPage<SecurityEventView> searchEvents(String scope, SecurityEventQuery query) { reads++; return page(); }
        @Override public Optional<SecurityEventView> findEventView(String scope, String id) { reads++; return Optional.of(SecurityEventView.of(id, scope)); }
        @Override public ManagementPage<AlertView> searchAlerts(String scope, AlertQuery query) { reads++; return page(); }
        @Override public Optional<AlertView> findAlertView(String scope, String id) { reads++; return Optional.of(AlertView.of(id, scope, "IN_PROGRESS", "analyst-1", 2)); }
        @Override public ManagementPage<AlertAssignmentView> searchAlertAssignments(String scope,String id,
            AlertAssignmentQuery query) { reads++; return page(); }
        @Override public boolean transitionAlert(String scope, String id, long version, String status,String actor,String reason,String dispositionId) { transitions++; return version == 1; }
        @Override public boolean assignAlert(String scope, String id, long version, String actor, String assignee,
                                             String reason, String dispositionId) {
            assignments++;
            if ((version != 0 && version != 1) || assignmentKey != null) return false;
            assignmentKey = dispositionId;
            assignmentWrites++;
            return true;
        }
        @Override public Optional<AlertView> findAlertAssignment(String scope, String id, long version, String actor,
                                                                 String assignee, String reason, String dispositionId) {
            return dispositionId.equals(assignmentKey) && version == 1 && "analyst-1".equals(assignee)
                ? Optional.of(AlertView.of(id, scope, "IN_PROGRESS", assignee, 2)) : Optional.<AlertView>empty();
        }
        @Override public ManagementPage<RuleView> searchRules(String scope, RuleQuery query) { reads++; return page(); }
        @Override public Optional<RuleView> findRuleView(String scope, String id) { reads++; return Optional.of(rule); }
        @Override public boolean changeRule(String scope, String id, long version, RuleMode mode, long threshold,
                                            String actorId, String approverId, String reason, String idempotencyKey) {
            if (version != rule.getVersion()) return false;
            rule = RuleView.of(id, scope, version + 1, mode, threshold);
            ruleKey = idempotencyKey;
            return true;
        }
        @Override public Optional<RuleView> findRuleChange(String scope, String id, long version, RuleMode mode,
                                                           long threshold, String actorId, String approverId,
                                                           String reason, String idempotencyKey) {
            return idempotencyKey.equals(ruleKey) && version + 1 == rule.getVersion()
                ? Optional.of(rule) : Optional.<RuleView>empty();
        }
        @Override public ManagementPage<WhitelistView> searchWhitelists(String scope, WhitelistQuery query) { reads++; return page(); }
        @Override public Optional<WhitelistView> findWhitelistView(String scope, String id) { reads++; return Optional.of(WhitelistView.of(id, scope)); }
        @Override public boolean transitionWhitelist(String scope, String id, long version, boolean active, String actorId, String reason) { transitions++; return true; }
        @Override public ManagementPage<ControlView> searchControls(String scope, ControlQuery query) { reads++; return page(); }
        @Override public Optional<ControlView> findControlView(String scope, String id) { reads++; return Optional.of(ControlView.of(id, scope, "AWAITING_APPROVAL", 1)); }
        @Override public Optional<io.github.jasper.monitoring.core.domain.ControlCommand> findControlCommand(String scope,String id){return Optional.of(new ControlCommand(scope,id,"alert-1","user:alice",ControlActionType.REQUIRE_APPROVAL,Instant.parse("2026-07-26T00:01:00Z"),"AUTH-01"));}
        @Override public boolean transitionControl(String scope, String id, long version, String expected, String target, String reason) { transitions++; return true; }
        private static <T> ManagementPage<T> page() { return ManagementPage.of(Collections.<T>emptyList(), 0, 20, 0); }
    }
}
