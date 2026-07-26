package io.github.jasper.monitoring.mybatis.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.port.EventRepository;
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
