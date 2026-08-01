package io.github.jasper.monitoring.mybatis.repository;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.control.ControlCatalog;
import io.github.jasper.monitoring.api.control.ControlStatus;
import io.github.jasper.monitoring.api.control.ControlType;
import io.github.jasper.monitoring.core.application.control.ControlExecutionService;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.port.ControlHandler;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
import static org.junit.jupiter.api.Assertions.*;
import org.apache.ibatis.exceptions.PersistenceException;

class ControlConcurrencyTest {
    @Test void activeAuthenticationControlsAreSystemScopedSuccessfulAndUnexpired() throws Exception {
        DataSource source = new UnpooledDataSource("org.h2.Driver",
            "jdbc:h2:mem:active-auth-controls;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        schema(source);
        MyBatisControlExecutionStore store = new MyBatisControlExecutionStore(factory(source));
        Instant now = Instant.parse("2026-08-01T00:00:00Z");

        store.reserve(command("system-a", "active", now.plusSeconds(60)), ControlStatus.SUCCEEDED, now);
        store.reserve(command("system-b", "other-system", now.plusSeconds(60)), ControlStatus.SUCCEEDED, now);
        store.reserve(command("system-a", "expired", now.minusSeconds(1)), ControlStatus.SUCCEEDED, now);
        store.reserve(command("system-a", "failed", now.plusSeconds(60)), ControlStatus.FAILED, now);
        store.reserve(command("system-a", "pending", now.plusSeconds(60)), ControlStatus.PENDING, now);

        List<ControlCommand> active = store.findActive("system-a", "v1:subject", now);

        assertEquals(1, active.size());
        assertEquals("active", active.get(0).getIdempotencyKey());
        assertEquals("system-a", active.get(0).getSystemId());
    }

    @Test void concurrentExecutionReservesOnceBeforeHostSideEffect() throws Exception {
        DataSource dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:control-race;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        schema(dataSource); SqlSessionFactory factory = factory(dataSource);
        AtomicInteger effects = new AtomicInteger(); CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
        ControlHandler handler = new ControlHandler() {
            public boolean supports(ControlActionType action) { return action == ControlActionType.LOCK; }
            public ControlExecution execute(ControlCommand command) {
                assertEquals("PENDING", status(dataSource, command.getIdempotencyKey()));
                effects.incrementAndGet(); entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return ControlExecution.succeeded(command.getIdempotencyKey());
            }
        };
        ControlCatalog<ControlHandler> catalog = ControlCatalog.<ControlHandler>builder().bind(ControlType.LOCK, handler).freeze();
        ControlExecutionService first = new ControlExecutionService(new MyBatisControlExecutionStore(factory), catalog, Clock.systemUTC());
        ControlExecutionService second = new ControlExecutionService(new MyBatisControlExecutionStore(factory), catalog, Clock.systemUTC());
        ControlCommand command = new ControlCommand("test-system", "race-key", "alert", "user", ControlActionType.LOCK, null, "rule");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ControlExecution> one = pool.submit(() -> first.execute(command)); entered.await();
            Future<ControlExecution> two = pool.submit(() -> second.execute(command));
            assertEquals(ControlStatus.PENDING, two.get().getStatus());
            release.countDown(); assertEquals(ControlStatus.SUCCEEDED, one.get().getStatus());
            assertEquals(1, effects.get());
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void persistsReplayRetryApprovalRejectAndPropagatesDatabaseFailure() throws Exception {
        DataSource source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:control-transitions;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        schema(source); SqlSessionFactory factory = factory(source); AtomicInteger calls = new AtomicInteger();
        ControlHandler handler = new ControlHandler() {
            public boolean supports(ControlActionType action) { return true; }
            public ControlExecution execute(ControlCommand command) {
                return calls.incrementAndGet() == 1 ? ControlExecution.failed(command.getIdempotencyKey(), "temporary")
                    : ControlExecution.succeeded(command.getIdempotencyKey());
            }
        };
        ControlCatalog<ControlHandler> catalog = ControlCatalog.<ControlHandler>builder()
            .bind(ControlType.LOCK, handler).bind(ControlType.REQUIRE_APPROVAL, handler).freeze();
        ControlExecutionService service = new ControlExecutionService(new MyBatisControlExecutionStore(factory), catalog, Clock.systemUTC());
        ControlCommand automatic = new ControlCommand("test-system", "retry-db", "a", "s", ControlActionType.LOCK, null, "r");
        assertEquals(ControlStatus.FAILED, service.execute(automatic).getStatus());
        assertEquals(ControlStatus.FAILED, service.execute(automatic).getStatus());
        assertEquals(ControlStatus.SUCCEEDED, service.retry(automatic).getStatus());
        assertEquals(2, calls.get());

        ControlCommand approval = new ControlCommand("test-system", "approval-db", "a", "s", ControlActionType.REQUIRE_APPROVAL, null, "r");
        assertEquals(ControlStatus.AWAITING_APPROVAL, service.execute(approval).getStatus());
        assertEquals(ControlStatus.SUCCEEDED, service.approve(approval).getStatus());
        ControlCommand rejection = new ControlCommand("test-system", "reject-db", "a", "s", ControlActionType.REQUIRE_APPROVAL, null, "r");
        service.execute(rejection); assertEquals(ControlStatus.REJECTED, service.reject("reject-db", "denied").getStatus());
        assertTrue(attemptCount(source, "retry-db") >= 4);

        DataSource broken = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:control-broken;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        ControlExecutionService brokenService = new ControlExecutionService(new MyBatisControlExecutionStore(factory(broken)), catalog, Clock.systemUTC());
        assertThrows(PersistenceException.class, () -> brokenService.execute(
            new ControlCommand("test-system", "db-failure", "a", "s", ControlActionType.LOCK, null, "r")));
    }

    private static String status(DataSource source, String key) {
        try (java.sql.Connection c = source.getConnection(); java.sql.PreparedStatement s = c.prepareStatement("SELECT status FROM monitoring_control_action WHERE idempotency_key=?")) {
            s.setString(1, key); try (java.sql.ResultSet r = s.executeQuery()) { assertTrue(r.next()); return r.getString(1); }
        } catch (java.sql.SQLException e) { throw new AssertionError(e); }
    }
    private static int attemptCount(DataSource source, String controlId) throws Exception {
        try (java.sql.Connection c = source.getConnection(); java.sql.PreparedStatement s = c.prepareStatement("SELECT COUNT(*) FROM monitoring_control_action_attempt WHERE control_id=?")) {
            s.setString(1, controlId); try (java.sql.ResultSet r = s.executeQuery()) { r.next(); return r.getInt(1); }
        }
    }
    private static ControlCommand command(String systemId, String key, Instant expiresAt) {
        return new ControlCommand(systemId, key, "alert", "v1:subject", ControlActionType.LOCK,
            expiresAt, "AUTH-TEST");
    }
    private static SqlSessionFactory factory(DataSource source) { return new SqlSessionFactoryBuilder().build(new Configuration(new Environment("test", new JdbcTransactionFactory(), source))); }
    private void schema(DataSource source) throws Exception {
        InputStream input = getClass().getResourceAsStream("/db/monitoring-schema.sql");
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8); java.sql.Connection c = source.getConnection()) {
            ScriptRunner runner = new ScriptRunner(c); runner.setLogWriter(null); runner.setErrorLogWriter(null); runner.runScript(reader);
        }
    }
}
