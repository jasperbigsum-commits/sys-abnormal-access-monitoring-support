package io.github.jasper.monitoring.mybatis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.AlertStatus;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.ControlStatus;
import io.github.jasper.monitoring.api.DispositionType;
import io.github.jasper.monitoring.api.EventFactSource;
import io.github.jasper.monitoring.api.EventInputIssue;
import io.github.jasper.monitoring.api.EventInputIssueCode;
import io.github.jasper.monitoring.api.EventInputStatus;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.RuleMode;
import io.github.jasper.monitoring.api.RuleSource;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.domain.ControlRecord;
import io.github.jasper.monitoring.core.domain.AlertDisposition;
import io.github.jasper.monitoring.core.port.MonitoringRepository;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.WhitelistEntry;
import io.github.jasper.monitoring.mybatis.po.PersistedRuleDefinition;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

class MyBatisMonitoringRepositoryTest {

    @Test
    void declaresMySqlSchemaConventionsAndChineseTableComments() throws Exception {
        String schema = readSchema();

        assertTrue(schema.contains("ENGINE=InnoDB"));
        assertTrue(schema.contains("DEFAULT CHARSET=utf8mb4"));
        assertTrue(schema.contains("COMMENT='安全事件明细表'"));
        assertTrue(schema.contains("rule_definition LONGTEXT NOT NULL"));
    }

    @Test
    void exposesInputQualityUpgradeOnlyThroughExplicitHostResource() {
        assertNull(getClass().getResource("/db/migration/V2__event_input_quality.sql"));
        assertNotNull(getClass().getResource("/db/upgrade/monitoring-event-input-quality-v2.sql"));
        assertNotNull(getClass().getResource("/db/upgrade/monitoring-control-rule-id-v3.sql"));
    }

    @Test
    void rollsBackAllMonitoringWritesWhenTransactionWorkFails() throws Exception {
        DataSource dataSource = new UnpooledDataSource(
            "org.h2.Driver", "jdbc:h2:mem:monitoring-rollback;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        executeSchema(dataSource);
        Configuration configuration = new Configuration(new org.apache.ibatis.mapping.Environment(
            "test", new JdbcTransactionFactory(), dataSource));
        MonitoringRepository repository = MyBatisMonitoringRepositoryRegistrar.create(configuration);
        Instant now = Instant.parse("2026-07-22T01:00:00Z");

        assertThrows(IllegalStateException.class, () -> repository.inTransaction(() -> {
            repository.saveAlert(new SecurityAlert("alert-rollback", "AUTH-01", RiskLevel.HIGH,
                "AUTH-01:alice", "alice", AlertStatus.NEW, now, now, 1));
            throw new IllegalStateException("simulated failure");
        }));

        assertFalse(repository.findAlert("alert-rollback").isPresent());
    }

    @Test
    void retrievesAlertsAndAppendsDispositionHistoryWithoutChangingEvents() throws Exception {
        DataSource dataSource = new UnpooledDataSource(
            "org.h2.Driver", "jdbc:h2:mem:monitoring-lifecycle;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        executeSchema(dataSource);
        Configuration configuration = new Configuration(new org.apache.ibatis.mapping.Environment(
            "test", new JdbcTransactionFactory(), dataSource));
        MonitoringRepository repository = MyBatisMonitoringRepositoryRegistrar.create(configuration);

        Instant occurredAt = Instant.parse("2026-07-22T01:00:00Z");
        SecurityEvent event = SecurityEvent.builder()
            .eventId("event-lifecycle")
            .systemId("orders")
            .eventType(SecurityEventType.LOGIN_FAILURE)
            .occurredAt(occurredAt)
            .receivedAt(occurredAt)
            .userId("alice")
            .accountType(AccountType.PERSON)
            .sourceIp("203.0.113.8")
            .requestId("request-lifecycle")
            .action("LOGIN")
            .result(SecurityEventResult.FAILURE)
            .build();
        repository.saveEvent(event);
        SecurityAlert alert = new SecurityAlert("alert-lifecycle", "AUTH-01", RiskLevel.HIGH, "AUTH-01:alice",
            "alice", AlertStatus.NEW, occurredAt, occurredAt, 1);
        repository.saveAlert(alert);

        assertEquals(alert.getAlertId(), repository.findAlert("alert-lifecycle").get().getAlertId());
        AlertDisposition disposition = new AlertDisposition("disposition-lifecycle", alert.getAlertId(),
            DispositionType.ACKNOWLEDGED, "operator-1", "Investigating", null,
            Instant.parse("2026-07-22T01:01:00Z"));
        repository.appendAlertDisposition(disposition);

        assertEquals(1, repository.findAlertDispositions(alert.getAlertId()).size());
        assertEquals(DispositionType.ACKNOWLEDGED,
            repository.findAlertDispositions(alert.getAlertId()).get(0).getDispositionType());
        assertEquals(event.getEventId(), repository.findEventsSince(occurredAt).get(0).getEventId());
        assertEquals(1, eventCount(dataSource));
    }

    @Test
    void registersAdministrationMapperForRuleVersionsAndDispositionHistory() throws Exception {
        DataSource dataSource = new UnpooledDataSource(
            "org.h2.Driver", "jdbc:h2:mem:monitoring-admin;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        executeSchema(dataSource);
        Configuration configuration = new Configuration(new org.apache.ibatis.mapping.Environment(
            "test", new JdbcTransactionFactory(), dataSource));
        MyBatisMonitoringRepositoryRegistrar.register(configuration);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);

        try (SqlSession session = factory.openSession(false)) {
            MonitoringAdministrationMapper mapper = session.getMapper(MonitoringAdministrationMapper.class);
            mapper.insertRule("AUTH-01", 1, "Login failures", "count failures", RiskLevel.HIGH, RuleMode.ENFORCE,
                true, Instant.parse("2026-07-22T01:00:00Z"), "security-admin");
            mapper.insertRule("AUTH-01", 2, "Login failures v2", "count failures >= 6", RiskLevel.HIGH,
                RuleMode.ALERT_ONLY, false, Instant.parse("2026-07-22T01:02:00Z"), "security-admin");
            List<PersistedRuleDefinition> versions = mapper.findRuleVersions();
            assertEquals(2, versions.size());
            assertEquals(2, versions.get(0).getRuleVersion());
            assertEquals(RuleSource.PERSISTED, versions.get(0).getSource());
            assertTrue(versions.get(0).isMutable());
            assertFalse(versions.get(0).isEnabled());
            assertEquals(1, mapper.setRuleEnabled("AUTH-01", 2, true));
            mapper.appendAlertDisposition("disposition-1", "alert-1", DispositionType.ACKNOWLEDGED, "security-admin",
                "Investigating", "ticket-42", Instant.parse("2026-07-22T01:05:00Z"));
            mapper.insertWhitelist("AUTH-01", "alice", "Approved test window", "security-admin",
                Instant.parse("2026-07-22T02:00:00Z"), Instant.parse("2026-07-22T01:00:00Z"));
            session.commit();
        }

        assertEquals(4, administrationEntryCount(dataSource));
        try (SqlSession session = factory.openSession()) {
            PersistedRuleDefinition current = session.getMapper(MonitoringAdministrationMapper.class)
                .findRuleVersions().get(0);
            assertTrue(current.isEnabled());
        }
    }

    @Test
    void persistsAndRetrievesMonitoringPortDataAgainstMySqlCompatibleH2() throws Exception {
        DataSource dataSource = new UnpooledDataSource(
            "org.h2.Driver", "jdbc:h2:mem:monitoring;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        executeSchema(dataSource);
        Configuration configuration = new Configuration(new org.apache.ibatis.mapping.Environment(
            "test", new JdbcTransactionFactory(), dataSource));
        MonitoringRepository repository = MyBatisMonitoringRepositoryRegistrar.create(configuration);

        Instant occurredAt = Instant.parse("2026-07-22T01:02:03Z");
        Instant receivedAt = Instant.parse("2026-07-22T01:02:04Z");
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        attributes.put("client", "web");
        attributes.put("tenant", "acme");
        SecurityEvent event = SecurityEvent.builder()
            .eventId("event-1")
            .systemId("orders")
            .eventType(SecurityEventType.LOGIN_FAILURE)
            .occurredAt(occurredAt)
            .receivedAt(receivedAt)
            .userId("alice")
            .accountType(AccountType.PERSON)
            .roleIds(new LinkedHashSet<String>(Arrays.asList("operator", "auditor")))
            .sourceIp("203.0.113.8")
            .deviceIdHash("device-hash")
            .sessionIdHash("session-hash")
            .requestId("req-1")
            .traceId("trace-1")
            .action("LOGIN")
            .result(SecurityEventResult.FAILURE)
            .reasonCode("INVALID_PASSWORD")
            .resourceType("account")
            .resourceId("alice")
            .orgScope("acme")
            .dataCount(0L)
            .latencyMs(12L)
            .attributes(attributes)
            .build();
        repository.saveEvent(event);

        List<SecurityEvent> events = repository.findEventsSince(occurredAt);
        assertEquals(1, events.size());
        SecurityEvent storedEvent = events.get(0);
        assertEquals(event.getEventId(), storedEvent.getEventId());
        assertEquals(event.getOccurredAt(), storedEvent.getOccurredAt());
        assertEquals(event.getReceivedAt(), storedEvent.getReceivedAt());
        assertEquals(event.getRoleIds(), storedEvent.getRoleIds());
        assertEquals(event.getAttributes(), storedEvent.getAttributes());
        assertEquals(0L, storedEvent.getDataCount());
        assertEquals(12L, storedEvent.getLatencyMs());
        assertTrue(storedEvent.hasDataCount());
        assertTrue(storedEvent.hasLatencyMs());

        SecurityAlert alert = new SecurityAlert(
            "alert-1", "AUTH-01", RiskLevel.HIGH, "AUTH-01:alice", "alice", AlertStatus.NEW,
            occurredAt, receivedAt, 1);
        repository.saveAlert(alert);
        repository.linkAlertEvent(alert.getAlertId(), event.getEventId());
        assertEquals(1, alertEventLinkCount(dataSource, alert.getAlertId(), event.getEventId()));
        SecurityAlert observedAlert = alert.observed(Instant.parse("2026-07-22T01:03:00Z"));
        repository.saveAlert(observedAlert);
        SecurityAlert storedAlert = repository.findOpenAlert("AUTH-01:alice").get();
        assertEquals(alert.getAlertId(), storedAlert.getAlertId());
        assertEquals(observedAlert.getEventCount(), storedAlert.getEventCount());
        assertEquals(observedAlert.getLastSeen(), storedAlert.getLastSeen());

        ControlCommand command = new ControlCommand(
            "control-key", alert.getAlertId(), "alice", ControlActionType.LOCK,
            Instant.parse("2026-07-22T02:00:00Z"), "AUTH-01");
        ControlRecord control = new ControlRecord(command,
            ControlExecution.succeeded(command.getIdempotencyKey()), receivedAt);
        repository.saveControl(control);
        ControlRecord storedControl = repository.findControl(command.getIdempotencyKey()).get();
        assertEquals(ControlStatus.SUCCEEDED, storedControl.getExecution().getStatus());
        assertEquals(command.getAction(), storedControl.getCommand().getAction());
        assertEquals("AUTH-01", storedControl.getCommand().getRuleId());
        assertEquals(command.getExpiresAt(), storedControl.getCommand().getExpiresAt());

        ControlCommand fallbackCommand = new ControlCommand(
            "fallback-key", alert.getAlertId(), "alice", ControlActionType.RATE_LIMIT,
            Instant.parse("2026-07-22T02:00:00Z"));
        repository.saveControl(new ControlRecord(fallbackCommand,
            ControlExecution.fallbackSkipped(fallbackCommand.getIdempotencyKey(), fallbackCommand.getAction()), receivedAt));
        ControlRecord storedFallback = repository.findControl(fallbackCommand.getIdempotencyKey()).get();
        assertTrue(storedFallback.getExecution().isDefaultFallback());
        assertEquals("DEFAULT_TRIGGER_REQUIRES_HOST_HANDLER:RATE_LIMIT",
            storedFallback.getExecution().getFailureReason());

        repository.addWhitelist(new WhitelistEntry("AUTH-01", "alice", Instant.parse("2026-07-22T01:30:00Z")));
        assertTrue(repository.isWhitelisted("AUTH-01", "alice", Instant.parse("2026-07-22T01:29:59Z")));
        assertFalse(repository.isWhitelisted("AUTH-01", "alice", Instant.parse("2026-07-22T01:30:00Z")));
    }

    @Test
    void persistsOrderedInputQualityMetadataWithoutChangingRolesOrAttributes() throws Exception {
        DataSource dataSource = new UnpooledDataSource(
            "org.h2.Driver", "jdbc:h2:mem:monitoring-input-quality;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        executeSchema(dataSource);
        Configuration configuration = new Configuration(new org.apache.ibatis.mapping.Environment(
            "test", new JdbcTransactionFactory(), dataSource));
        MonitoringRepository repository = MyBatisMonitoringRepositoryRegistrar.create(configuration);
        Instant occurredAt = Instant.parse("2026-07-24T01:02:03Z");
        EventInputIssue firstIssue = EventInputIssue.of("EXPT-02", "resourceId", EventInputIssueCode.MISSING_RESOURCE_ID,
            EventFactSource.SERVER_COMPUTED);
        EventInputIssue secondIssue = EventInputIssue.of("AUTH-01", "reasonCode", EventInputIssueCode.MISSING_REASON_CODE,
            EventFactSource.METHOD_PARAMETER);
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        attributes.put("channel", "api");
        SecurityEvent event = SecurityEvent.builder()
            .eventId("event-input-quality")
            .systemId("orders")
            .eventType(SecurityEventType.EXPORT)
            .occurredAt(occurredAt)
            .receivedAt(occurredAt)
            .userId("auditor")
            .accountType(AccountType.PERSON)
            .roleIds(new LinkedHashSet<String>(Arrays.asList("auditor", "operator")))
            .sourceIp("203.0.113.17")
            .requestId("request-input-quality")
            .action("EXPORT")
            .result(SecurityEventResult.SUCCESS)
            .inputStatus(EventInputStatus.INCOMPLETE)
            .inputIssues(Arrays.asList(firstIssue, secondIssue))
            .attributes(attributes)
            .build();

        repository.saveEvent(event);

        SecurityEvent storedEvent = repository.findEventsSince(occurredAt).get(0);
        assertEquals(EventInputStatus.INCOMPLETE, storedEvent.getInputStatus());
        assertFalse(storedEvent.hasDataCount());
        assertFalse(storedEvent.hasLatencyMs());
        assertEquals(Arrays.asList(firstIssue, secondIssue), storedEvent.getInputIssues());
        assertEquals(event.getRoleIds(), storedEvent.getRoleIds());
        assertEquals(event.getAttributes(), storedEvent.getAttributes());
    }

    @Test
    void rebuildsLegacyEventsWithUnknownInputQualityDefaults() throws Exception {
        DataSource dataSource = new UnpooledDataSource(
            "org.h2.Driver", "jdbc:h2:mem:monitoring-input-quality-legacy;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        executeSchema(dataSource);
        Instant occurredAt = Instant.parse("2026-07-24T02:02:03Z");
        insertLegacyEvent(dataSource, occurredAt);
        Configuration configuration = new Configuration(new org.apache.ibatis.mapping.Environment(
            "test", new JdbcTransactionFactory(), dataSource));
        MonitoringRepository repository = MyBatisMonitoringRepositoryRegistrar.create(configuration);

        SecurityEvent storedEvent = repository.findEventsSince(occurredAt).get(0);

        assertEquals(EventInputStatus.UNKNOWN, storedEvent.getInputStatus());
        assertFalse(storedEvent.hasDataCount());
        assertFalse(storedEvent.hasLatencyMs());
        assertTrue(storedEvent.getInputIssues().isEmpty());
    }

    @Test
    void migratesLegacySchemaBeforePersistingInputQuality() throws Exception {
        DataSource dataSource = new UnpooledDataSource(
            "org.h2.Driver", "jdbc:h2:mem:monitoring-input-quality-migration;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        executeSchema(dataSource);
        removeInputQualitySchema(dataSource);
        Instant legacyOccurredAt = Instant.parse("2026-07-24T04:02:03Z");
        insertLegacyEvent(dataSource, legacyOccurredAt);

        executeHostControlledUpgrade(dataSource);

        Configuration configuration = new Configuration(new org.apache.ibatis.mapping.Environment(
            "test", new JdbcTransactionFactory(), dataSource));
        MonitoringRepository repository = MyBatisMonitoringRepositoryRegistrar.create(configuration);
        SecurityEvent legacyEvent = repository.findEventsSince(legacyOccurredAt).get(0);
        assertEquals(EventInputStatus.UNKNOWN, legacyEvent.getInputStatus());
        assertFalse(legacyEvent.hasDataCount());
        assertFalse(legacyEvent.hasLatencyMs());
        assertTrue(legacyEvent.getInputIssues().isEmpty());

        Instant qualityOccurredAt = legacyOccurredAt.plusSeconds(1L);
        EventInputIssue issue = EventInputIssue.of("EXPT-01", "resourceId", EventInputIssueCode.MISSING_RESOURCE_ID,
            EventFactSource.SERVER_COMPUTED);
        SecurityEvent qualityEvent = SecurityEvent.builder()
            .eventId("event-migrated-quality")
            .systemId("orders")
            .eventType(SecurityEventType.EXPORT)
            .occurredAt(qualityOccurredAt)
            .receivedAt(qualityOccurredAt)
            .userId("auditor")
            .accountType(AccountType.PERSON)
            .sourceIp("203.0.113.20")
            .requestId("request-migrated-quality")
            .action("EXPORT")
            .result(SecurityEventResult.SUCCESS)
            .dataCount(0L)
            .latencyMs(5L)
            .inputStatus(EventInputStatus.INCOMPLETE)
            .inputIssues(Arrays.asList(issue))
            .build();
        repository.saveEvent(qualityEvent);

        SecurityEvent storedQualityEvent = repository.findEventsSince(qualityOccurredAt).get(0);
        assertEquals(EventInputStatus.INCOMPLETE, storedQualityEvent.getInputStatus());
        assertEquals(Arrays.asList(issue), storedQualityEvent.getInputIssues());
        assertEquals(0L, storedQualityEvent.getDataCount());
        assertEquals(5L, storedQualityEvent.getLatencyMs());
        assertTrue(storedQualityEvent.hasDataCount());
        assertTrue(storedQualityEvent.hasLatencyMs());
    }

    @Test
    void rejectsMalformedPersistedInputIssuesWithoutEchoingStoredValues() throws Exception {
        DataSource dataSource = new UnpooledDataSource(
            "org.h2.Driver", "jdbc:h2:mem:monitoring-input-quality-malformed;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        executeSchema(dataSource);
        Instant occurredAt = Instant.parse("2026-07-24T03:02:03Z");
        insertLegacyEvent(dataSource, occurredAt);
        String malformedIssueCode = "UNSUPPORTED_ISSUE_CODE";
        insertMalformedInputIssue(dataSource, "event-legacy-quality", malformedIssueCode);
        Configuration configuration = new Configuration(new org.apache.ibatis.mapping.Environment(
            "test", new JdbcTransactionFactory(), dataSource));
        MonitoringRepository repository = MyBatisMonitoringRepositoryRegistrar.create(configuration);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> repository.findEventsSince(occurredAt));

        assertEquals("Persisted event input issue is invalid", exception.getMessage());
        assertFalse(exception.getMessage().contains(malformedIssueCode));
    }

    private void executeSchema(DataSource dataSource) throws Exception {
        executeSqlResource(dataSource, "/db/monitoring-schema.sql");
    }

    private void executeHostControlledUpgrade(DataSource dataSource) throws Exception {
        executeSqlResource(dataSource, "/db/upgrade/monitoring-event-input-quality-v2.sql");
    }

    private void executeSqlResource(DataSource dataSource, String resourcePath) throws Exception {
        InputStream input = getClass().getResourceAsStream(resourcePath);
        if (input == null) {
            throw new IllegalStateException("Missing SQL resource: " + resourcePath);
        }
        try (Connection connection = dataSource.getConnection();
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            ScriptRunner runner = new ScriptRunner(connection);
            runner.setLogWriter(null);
            runner.setErrorLogWriter(null);
            runner.runScript(reader);
        }
    }

    private String readSchema() throws Exception {
        InputStream input = getClass().getResourceAsStream("/db/monitoring-schema.sql");
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            StringBuilder schema = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                schema.append(buffer, 0, read);
            }
            return schema.toString();
        }
    }

    private int alertEventLinkCount(DataSource dataSource, String alertId, String eventId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT COUNT(*) FROM alert_event_link WHERE alert_id = ? AND event_id = ?")) {
            statement.setString(1, alertId);
            statement.setString(2, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private int administrationEntryCount(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT (SELECT COUNT(*) FROM security_rule) + (SELECT COUNT(*) FROM alert_disposition) "
                     + "+ (SELECT COUNT(*) FROM security_whitelist)");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private int eventCount(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM security_event");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private void insertLegacyEvent(DataSource dataSource, Instant occurredAt) throws Exception {
        String sql = "INSERT INTO security_event (event_id, system_id, event_type, occurred_at, received_at, user_id, "
            + "account_type, source_ip, request_id, action, result, data_count, latency_ms) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "event-legacy-quality");
            statement.setString(2, "orders");
            statement.setString(3, SecurityEventType.EXPORT.name());
            statement.setObject(4, occurredAt);
            statement.setObject(5, occurredAt);
            statement.setString(6, "auditor");
            statement.setString(7, AccountType.PERSON.name());
            statement.setString(8, "203.0.113.18");
            statement.setString(9, "request-legacy-quality");
            statement.setString(10, "EXPORT");
            statement.setString(11, SecurityEventResult.SUCCESS.name());
            statement.setLong(12, 0L);
            statement.setLong(13, 0L);
            statement.executeUpdate();
        }
    }

    private void removeInputQualitySchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE security_event_input_issue");
            statement.execute("ALTER TABLE security_event DROP COLUMN data_count_known");
            statement.execute("ALTER TABLE security_event DROP COLUMN latency_ms_known");
            statement.execute("ALTER TABLE security_event DROP COLUMN input_status");
        }
    }

    private void insertMalformedInputIssue(DataSource dataSource, String eventId, String issueCode) throws Exception {
        String sql = "INSERT INTO security_event_input_issue (event_id, issue_index, rule_id, fact_name, issue_code, source_type) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, eventId);
            statement.setInt(2, 0);
            statement.setString(3, "AUTH-01");
            statement.setString(4, "resourceId");
            statement.setString(5, issueCode);
            statement.setString(6, EventFactSource.SERVER_COMPUTED.name());
            statement.executeUpdate();
        }
    }
}
