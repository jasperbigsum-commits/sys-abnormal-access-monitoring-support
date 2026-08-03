package io.github.jasper.monitoring.mybatis.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import io.github.jasper.monitoring.core.domain.NotificationDelivery;
import io.github.jasper.monitoring.core.domain.rule.RuleObservation;
import io.github.jasper.monitoring.core.application.notification.NotificationDeliveryService;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.core.port.EventRepository;
import io.github.jasper.monitoring.mybatis.mapper.RuleObservationMapper;
import io.github.jasper.monitoring.mybatis.po.RuleObservationPo;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
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
        ControlCommand command = new ControlCommand("orders", "control-ports", "alert-ports", "alice",
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
        Instant at = Instant.parse("2026-07-26T01:00:00Z");
        NotificationDelivery pending = NotificationDelivery.pending("delivery-1", "email", "alert-1", at);

        assertTrue(store.create(pending));
        assertEquals(false, store.create(pending));
        NotificationDelivery claimed = pending.claim(at.plusSeconds(1), at.plusSeconds(60));
        assertTrue(store.update(claimed, 0));
        assertEquals(false, store.update(claimed, 0));
        NotificationDelivery retry = claimed.failedAttempt("CHANNEL_FAILURE", at.plusSeconds(60), false,
            at.plusSeconds(2));
        assertTrue(store.update(retry, 1));

        NotificationDelivery stored = store.find("email", "alert-1").get();
        assertEquals(NotificationDelivery.Status.RETRY_PENDING, stored.getStatus());
        assertEquals(1, stored.getAttemptCount());
        assertEquals("CHANNEL_FAILURE", stored.getFailureCategory());
        assertEquals(0, store.findDue("email", at.plusSeconds(59), 10).size());
        assertEquals("delivery-1", store.findDue("email", at.plusSeconds(60), 10).get(0).getDeliveryId());

        NotificationDelivery retryClaim = retry.claim(at.plusSeconds(60), at.plusSeconds(120));
        assertTrue(store.update(retryClaim, retry.getVersion()));
        NotificationDelivery delivered = retryClaim.delivered(at.plusSeconds(61));
        assertTrue(store.update(delivered, retryClaim.getVersion()));
        assertEquals(NotificationDelivery.Status.DELIVERED,
            store.find("email", "alert-1").get().getStatus());
        assertEquals(0, store.findDue("email", at.plusSeconds(120), 10).size());
    }

    @Test
    void recoversACommittedPendingIntentBeforeItsFirstDelivery() throws Exception {
        DataSource dataSource = dataSource("store-notification-pending-recovery");
        executeSchema(dataSource);
        MyBatisMonitoringStore store = new MyBatisMonitoringStore(factory(dataSource));
        Instant at = Instant.parse("2026-07-26T01:00:00Z");
        SecurityAlert alert = new SecurityAlert("alert-pending-recovery", "AUTH-01", RiskLevel.HIGH,
            "fp-recovery", "alice", AlertStatus.NEW, at, at, 1);
        AtomicInteger calls = new AtomicInteger();
        NotificationDeliveryService deliveries = new NotificationDeliveryService("email",
            (deliveryId, value) -> calls.incrementAndGet(), store, store,
            Clock.fixed(at, ZoneOffset.UTC), 3, Duration.ofMinutes(1), Duration.ofMinutes(5));

        store.required(() -> {
            store.save(alert);
            deliveries.register(alert);
            return null;
        });
        assertEquals(NotificationDelivery.Status.PENDING,
            store.find("email", alert.getAlertId()).get().getStatus());

        deliveries.retryDue(10);

        assertEquals(1, calls.get());
        assertEquals(NotificationDelivery.Status.DELIVERED,
            store.find("email", alert.getAlertId()).get().getStatus());
    }

    @Test
    void upgradesLegacyNotificationRowsToVersionedState() throws Exception {
        DataSource dataSource = dataSource("store-notification-upgrade");
        try (java.sql.Connection connection = dataSource.getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE monitoring_notification_delivery (delivery_id VARCHAR(128) NOT NULL PRIMARY KEY, "
                + "channel VARCHAR(128) NOT NULL, aggregate_id VARCHAR(128) NOT NULL, status VARCHAR(32) NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE(channel, aggregate_id))");
            statement.execute("INSERT INTO monitoring_notification_delivery (delivery_id, channel, aggregate_id, status) "
                + "VALUES ('legacy-delivery', 'email', 'legacy-alert', 'PENDING')");
            statement.execute("INSERT INTO monitoring_notification_delivery (delivery_id, channel, aggregate_id, status) "
                + "VALUES ('legacy-delivered', 'email', 'legacy-delivered-alert', 'DELIVERED')");
            statement.execute("INSERT INTO monitoring_notification_delivery (delivery_id, channel, aggregate_id, status) "
                + "VALUES ('legacy-failed', 'email', 'legacy-failed-alert', 'FAILED')");
            statement.execute("INSERT INTO monitoring_notification_delivery (delivery_id, channel, aggregate_id, status) "
                + "VALUES ('legacy-retry', 'email', 'legacy-retry-alert', 'RETRY_PENDING')");
        }
        executeScript(dataSource, "/db/upgrade/monitoring-notification-retry-v7.sql");

        NotificationDelivery stored = new MyBatisMonitoringStore(factory(dataSource))
            .find("email", "legacy-alert").get();

        assertEquals("legacy-delivery", stored.getDeliveryId());
        assertEquals(NotificationDelivery.Status.PENDING, stored.getStatus());
        assertEquals(0, stored.getAttemptCount());
        assertEquals(0L, stored.getVersion());
        NotificationDelivery delivered = new MyBatisMonitoringStore(factory(dataSource))
            .find("email", "legacy-delivered-alert").get();
        assertEquals(NotificationDelivery.Status.DELIVERED, delivered.getStatus());
        assertEquals(1, delivered.getAttemptCount());
        NotificationDelivery failed = new MyBatisMonitoringStore(factory(dataSource))
            .find("email", "legacy-failed-alert").get();
        assertEquals("LEGACY_FAILURE", failed.getFailureCategory());
        NotificationDelivery retry = new MyBatisMonitoringStore(factory(dataSource))
            .find("email", "legacy-retry-alert").get();
        assertEquals(NotificationDelivery.Status.RETRY_PENDING, retry.getStatus());
        assertEquals("LEGACY_RETRY_PENDING", retry.getFailureCategory());
        assertNotNull(retry.getNextAttemptAt());
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

    @Test
    void roundTripsAttemptedAccountHashOnTheEventRow() throws Exception {
        DataSource dataSource = dataSource("store-attempted-account-hash");
        executeSchema(dataSource);
        EventRepository events = new MyBatisMonitoringStore(factory(dataSource));
        Instant at = Instant.parse("2026-08-03T00:00:00Z");
        SecurityEvent source = SecurityEvent.builder().eventId("event-login-attempt").systemId("orders")
            .eventType(SecurityEventType.LOGIN_FAILURE).occurredAt(at).receivedAt(at)
            .sourceIp("203.0.113.1").requestId("request-login-attempt").action("LOGIN")
            .result(SecurityEventResult.DENIED).attemptedAccountHash("v1:7H2c95hU0y8M3q5N6rT1sV4wX9zA")
            .build();

        events.save(source);

        assertEquals("v1:7H2c95hU0y8M3q5N6rT1sV4wX9zA",
            events.findEvent("event-login-attempt").get().getAttemptedAccountHash());
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
        executeScript(dataSource, "/db/monitoring-schema.sql");
    }
    private void executeScript(DataSource dataSource, String resource) throws Exception {
        InputStream input = getClass().getResourceAsStream(resource);
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
             java.sql.Connection connection = dataSource.getConnection()) {
            ScriptRunner runner = new ScriptRunner(connection);
            runner.setLogWriter(null); runner.setErrorLogWriter(null); runner.runScript(reader);
        }
    }
}
