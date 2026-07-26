package io.github.jasper.monitoring.mybatis.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import io.github.jasper.monitoring.api.AlertStatus;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.port.AlertRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
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

class MyBatisTransactionTest {
    @Test
    void rollsBackAlertWritesAsOneRequiredUnit() throws Exception {
        DataSource dataSource = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:store-rollback;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        executeSchema(dataSource);
        MyBatisMonitoringStore store = new MyBatisMonitoringStore(factory(dataSource));
        AlertRepository alerts = store;
        MonitoringTransaction transaction = store;
        Instant now = Instant.parse("2026-07-26T01:00:00Z");

        assertThrows(IllegalStateException.class, () -> transaction.required(() -> {
            alerts.save(new SecurityAlert("alert-rollback", "AUTH-01", RiskLevel.HIGH, "fp", "alice", AlertStatus.NEW, now, now, 1));
            throw new IllegalStateException("rollback");
        }));

        assertFalse(alerts.findAlert("alert-rollback").isPresent());
    }
    private SqlSessionFactory factory(DataSource dataSource) {
        return new SqlSessionFactoryBuilder().build(new Configuration(new Environment("test", new JdbcTransactionFactory(), dataSource)));
    }
    private void executeSchema(DataSource dataSource) throws Exception {
        InputStream input = getClass().getResourceAsStream("/db/monitoring-schema.sql");
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8); java.sql.Connection connection = dataSource.getConnection()) {
            ScriptRunner runner = new ScriptRunner(connection); runner.setLogWriter(null); runner.setErrorLogWriter(null); runner.runScript(reader);
        }
    }
}
