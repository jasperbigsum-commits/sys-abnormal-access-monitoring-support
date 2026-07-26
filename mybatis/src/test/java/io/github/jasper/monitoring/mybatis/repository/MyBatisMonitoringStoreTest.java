package io.github.jasper.monitoring.mybatis.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.AlertStatus;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.DispositionType;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.EventFactSource;
import io.github.jasper.monitoring.api.EventInputIssue;
import io.github.jasper.monitoring.api.EventInputIssueCode;
import io.github.jasper.monitoring.api.EventInputStatus;
import java.util.Arrays;
import java.util.Collections;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.domain.AlertDisposition;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.domain.ControlRecord;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.domain.WhitelistEntry;
import io.github.jasper.monitoring.core.domain.EventFact;
import io.github.jasper.monitoring.core.domain.rule.RuleObservation;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.core.port.EventRepository;
import io.github.jasper.monitoring.mybatis.mapper.RuleObservationMapper;
import io.github.jasper.monitoring.mybatis.po.RuleObservationPo;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

class MyBatisMonitoringStoreTest {
    @Test
    void roundTripsObserveOnlyRuleMatch() throws Exception {
        DataSource dataSource = dataSource("store-rule-observation");
        executeSchema(dataSource);
        SqlSessionFactory factory = factory(dataSource);
        MyBatisMonitoringStore store = new MyBatisMonitoringStore(factory);
        Instant at = Instant.parse("2026-07-26T01:00:00Z");
        store.save(event("event-observation", "orders", at));

        store.save(RuleObservation.of("observation-1", "AUTH-01", "event-observation",
            "alice", at));

        try (org.apache.ibatis.session.SqlSession session = factory.openSession()) {
            RuleObservationPo stored = session.getMapper(RuleObservationMapper.class)
                .find("observation-1");
            assertEquals("observation-1", stored.getObservationId());
            assertEquals("AUTH-01", stored.getRuleId());
            assertEquals("event-observation", stored.getEventId());
            assertEquals("alice", stored.getSubject());
            assertEquals(at, stored.getObservedAt());
        }
    }

    @Test
    void roundTripsAllNarrowPersistencePorts() throws Exception {
        DataSource dataSource = dataSource("store-narrow-ports");
        executeSchema(dataSource);
        MyBatisMonitoringStore store = new MyBatisMonitoringStore(factory(dataSource));
        Instant at = Instant.parse("2026-07-26T01:00:00Z");
        store.save(event("event-ports", "orders", at));
        store.save(new SecurityAlert("alert-ports", "AUTH-01", RiskLevel.HIGH, "fp", "alice",
            AlertStatus.NEW, at, at, 1));
        store.linkEvent("alert-ports", "event-ports");
        store.appendDisposition(new AlertDisposition("disposition-ports", "alert-ports",
            DispositionType.ACKNOWLEDGED, "operator-1", "Investigating", null, at));
        ControlCommand command = new ControlCommand("control-ports", "alert-ports", "alice",
            ControlActionType.REQUIRE_MFA, at.plusSeconds(300), "AUTH-01");
        store.save(new ControlRecord(command, ControlExecution.succeeded("control-ports"), at));
        store.add(new WhitelistEntry("AUTH-01", "alice", at.plusSeconds(300)));

        assertEquals(1, store.findDispositions("alert-ports").size());
        assertEquals(DispositionType.ACKNOWLEDGED,
            store.findDispositions("alert-ports").get(0).getDispositionType());
        assertTrue(store.findControl("control-ports").get().getExecution().isSucceeded());
        assertTrue(store.isActive("AUTH-01", "alice", at));
    }

    @Test
    void roundTripsTypedEventAndScopesHistoryBySystem() throws Exception {
        DataSource dataSource = dataSource("store-round-trip");
        executeSchema(dataSource);
        EventRepository events = new MyBatisMonitoringStore(factory(dataSource));
        Instant at = Instant.parse("2026-07-26T01:00:00Z");
        events.save(event("event-orders", "orders", at));

        assertTrue(events.findEvent("event-orders").isPresent());
        assertEquals(1, events.findSince("orders", at.minusSeconds(1)).size());
        assertEquals(0, events.findSince("billing", at.minusSeconds(1)).size());
    }

    @Test
    void roundTripsNotificationDeliveryState() throws Exception {
        DataSource dataSource = dataSource("store-notification");
        executeSchema(dataSource);
        MyBatisMonitoringStore store = new MyBatisMonitoringStore(factory(dataSource));
        store.record("delivery-1", "email", "alert-1", "PENDING");
        try (java.sql.Connection connection = dataSource.getConnection(); java.sql.PreparedStatement statement = connection.prepareStatement("SELECT status FROM notification_delivery WHERE delivery_id = ?")) {
            statement.setString(1, "delivery-1");
            try (java.sql.ResultSet result = statement.executeQuery()) { result.next(); assertEquals("PENDING", result.getString(1)); }
        }
    }

    @Test
    void roundTripsEventAssociations() throws Exception {
        DataSource dataSource = dataSource("store-associations");
        executeSchema(dataSource);
        EventRepository events = new MyBatisMonitoringStore(factory(dataSource));
        Instant at = Instant.parse("2026-07-26T01:00:00Z");
        EventInputIssue issue = EventInputIssue.of("AUTH-01", "resourceId", EventInputIssueCode.MISSING_RESOURCE_ID, EventFactSource.SERVER_COMPUTED);
        SecurityEvent source = SecurityEvent.builder().eventId("event-associations").systemId("orders").eventType(SecurityEventType.EXPORT)
            .occurredAt(at).receivedAt(at).sourceIp("203.0.113.1").requestId("request-associations").action("EXPORT")
            .result(SecurityEventResult.SUCCESS).roleIds(Collections.singleton("auditor"))
            .attributes(Collections.singletonMap("tenant", "acme")).inputStatus(EventInputStatus.INCOMPLETE)
            .inputIssues(Arrays.asList(issue)).facts(Collections.singletonList(
                new EventFact("data_count", Long.class.getName(), "42", FactSource.HOST_PROVIDER))).build();
        events.save(source);
        SecurityEvent stored = events.findEvent("event-associations").get();
        assertEquals(Collections.singleton("auditor"), stored.getRoleIds());
        assertEquals(Collections.singletonMap("tenant", "acme"), stored.getAttributes());
        assertEquals(Collections.singletonList(issue), stored.getInputIssues());
        assertEquals(1, stored.getFacts().size());
        assertEquals("data_count", stored.getFacts().get(0).getKey());
        assertEquals(FactSource.HOST_PROVIDER, stored.getFacts().get(0).getSource());
    }

    private SecurityEvent event(String id, String system, Instant at) {
        return SecurityEvent.builder().eventId(id).systemId(system).eventType(SecurityEventType.EXPORT)
            .occurredAt(at).receivedAt(at).sourceIp("203.0.113.1").requestId(id + "-request")
            .action("EXPORT").result(SecurityEventResult.SUCCESS).build();
    }
    private DataSource dataSource(String name) {
        return new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }
    private SqlSessionFactory factory(DataSource dataSource) {
        return new SqlSessionFactoryBuilder().build(new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource)));
    }
    private void executeSchema(DataSource dataSource) throws Exception {
        InputStream input = getClass().getResourceAsStream("/db/monitoring-schema.sql");
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
             java.sql.Connection connection = dataSource.getConnection()) {
            ScriptRunner runner = new ScriptRunner(connection);
            runner.setLogWriter(null); runner.setErrorLogWriter(null); runner.runScript(reader);
        }
    }
}
