package io.github.jasper.monitoring.core.application.management;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.jasper.monitoring.api.error.ManagementAccessDeniedException;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementAuthorizer;
import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.ManagementPageRequest;
import io.github.jasper.monitoring.api.management.command.AlertAcknowledgeCommand;
import io.github.jasper.monitoring.api.management.model.AlertView;
import io.github.jasper.monitoring.api.management.model.ControlView;
import io.github.jasper.monitoring.api.management.model.RuleView;
import io.github.jasper.monitoring.api.management.model.SecurityEventView;
import io.github.jasper.monitoring.api.management.model.WhitelistView;
import io.github.jasper.monitoring.api.management.query.AlertQuery;
import io.github.jasper.monitoring.api.management.query.ControlQuery;
import io.github.jasper.monitoring.api.management.query.RuleQuery;
import io.github.jasper.monitoring.api.management.query.SecurityEventQuery;
import io.github.jasper.monitoring.api.management.query.WhitelistQuery;
import io.github.jasper.monitoring.core.domain.management.ManagementAuditRecord;
import io.github.jasper.monitoring.core.port.ManagementAuditRepository;
import io.github.jasper.monitoring.core.port.ManagementQueryRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ManagementServiceAuthorizationTest {
    private final ManagementActor actor = ManagementActor.of("operator-1", "system-a");
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

    private static final class RecordingAudits implements ManagementAuditRepository {
        private final List<ManagementAuditRecord> records = new ArrayList<ManagementAuditRecord>();
        @Override public void append(ManagementAuditRecord record) { records.add(record); }
    }

    private static final class FakeQueries implements ManagementQueryRepository {
        private int reads;
        private int transitions;
        @Override public ManagementPage<SecurityEventView> searchEvents(String scope, SecurityEventQuery query) { reads++; return page(); }
        @Override public Optional<SecurityEventView> findEventView(String scope, String id) { reads++; return Optional.of(SecurityEventView.of(id, scope)); }
        @Override public ManagementPage<AlertView> searchAlerts(String scope, AlertQuery query) { reads++; return page(); }
        @Override public Optional<AlertView> findAlertView(String scope, String id) { reads++; return Optional.of(AlertView.of(id, scope, 2)); }
        @Override public boolean transitionAlert(String scope, String id, long version, String status) { transitions++; return version == 1; }
        @Override public ManagementPage<RuleView> searchRules(String scope, RuleQuery query) { reads++; return page(); }
        @Override public Optional<RuleView> findRuleView(String scope, String id) { reads++; return Optional.of(RuleView.of(id, scope)); }
        @Override public ManagementPage<WhitelistView> searchWhitelists(String scope, WhitelistQuery query) { reads++; return page(); }
        @Override public Optional<WhitelistView> findWhitelistView(String scope, String id) { reads++; return Optional.of(WhitelistView.of(id, scope)); }
        @Override public boolean transitionWhitelist(String scope, String id, long version, boolean active, String actorId, String reason) { transitions++; return true; }
        @Override public ManagementPage<ControlView> searchControls(String scope, ControlQuery query) { reads++; return page(); }
        @Override public Optional<ControlView> findControlView(String scope, String id) { reads++; return Optional.of(ControlView.of(id, scope, "PENDING", 2)); }
        @Override public boolean transitionControl(String scope, String id, long version, String expected, String target, String reason) { transitions++; return true; }
        private static <T> ManagementPage<T> page() { return ManagementPage.of(Collections.<T>emptyList(), 0, 20, 0); }
    }
}
