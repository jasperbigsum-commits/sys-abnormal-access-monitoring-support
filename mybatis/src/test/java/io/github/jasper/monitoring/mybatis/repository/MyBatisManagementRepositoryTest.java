package io.github.jasper.monitoring.mybatis.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementOperation;
import io.github.jasper.monitoring.api.management.ManagementPageRequest;
import io.github.jasper.monitoring.api.management.query.SecurityEventQuery;
import io.github.jasper.monitoring.core.domain.management.ManagementAuditRecord;
import io.github.jasper.monitoring.mybatis.MyBatisMonitoringRepositoryRegistrar;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.session.SqlSessionManager;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MyBatisManagementRepositoryTest {
    private DataSource dataSource;
    private MyBatisManagementRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new PooledDataSource("org.h2.Driver",
            "jdbc:h2:mem:management-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection=dataSource.getConnection(); Statement statement=connection.createStatement()) {
            statement.execute("CREATE TABLE security_event(event_id VARCHAR(128) PRIMARY KEY,system_id VARCHAR(128),occurred_at TIMESTAMP,action VARCHAR(128),result VARCHAR(32))");
            statement.execute("CREATE TABLE management_audit(audit_id VARCHAR(128) PRIMARY KEY,system_id VARCHAR(128),actor_id VARCHAR(128),action VARCHAR(128),target_type VARCHAR(64),target_id VARCHAR(128),outcome VARCHAR(32),occurred_at TIMESTAMP)");
            statement.execute("CREATE TABLE security_alert(alert_id VARCHAR(128) PRIMARY KEY,status VARCHAR(32),version BIGINT,last_seen TIMESTAMP)");
            statement.execute("CREATE TABLE alert_event_link(alert_id VARCHAR(128),event_id VARCHAR(128))");
            statement.execute("CREATE TABLE alert_disposition(disposition_id VARCHAR(128) PRIMARY KEY,alert_id VARCHAR(128),disposition_type VARCHAR(64),operator_id VARCHAR(128),comment_text VARCHAR(1024),evidence_summary VARCHAR(1024),created_at TIMESTAMP)");
            statement.execute("INSERT INTO security_event VALUES('event-a','system-a',TIMESTAMP '2026-07-20 00:00:00','report:export','SUCCESS')");
            statement.execute("INSERT INTO security_event VALUES('event-b','system-b',TIMESTAMP '2026-07-20 00:00:00','report:export','SUCCESS')");
            statement.execute("INSERT INTO security_alert VALUES('alert-a','NEW',1,TIMESTAMP '2026-07-20 00:00:00')");
            statement.execute("INSERT INTO alert_event_link VALUES('alert-a','event-a')");
        }
        Configuration configuration=new Configuration(new Environment("test",new JdbcTransactionFactory(),dataSource));
        MyBatisMonitoringRepositoryRegistrar.register(configuration);
        SqlSessionFactory factory=new SqlSessionFactoryBuilder().build(configuration);
        repository=new MyBatisManagementRepository(SqlSessionManager.newInstance(factory));
    }

    @Test
    void queriesOnlyTheAuthorizedScopeAndAppendsSanitizedAudit() throws Exception {
        SecurityEventQuery query=SecurityEventQuery.of(
            ManagementPageRequest.of(0,20,SecurityEventQuery.Sort.OCCURRED_AT),
            Instant.parse("2026-07-01T00:00:00Z"),Instant.parse("2026-07-26T00:00:00Z"));

        assertEquals(1,repository.searchEvents("system-a",query).getItems().size());
        assertTrue(repository.findEventView("system-a","event-a").isPresent());
        assertFalse(repository.findEventView("system-a","event-b").isPresent());

        repository.append(new ManagementAuditRecord("audit-1",ManagementActor.of("operator","system-a"),
            ManagementOperation.EVENT_READ,"security-event","event-a",ManagementAuditRecord.Outcome.SUCCEEDED,
            Instant.parse("2026-07-26T00:00:00Z")));
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement();
            ResultSet rows=statement.executeQuery("SELECT actor_id,outcome FROM management_audit WHERE audit_id='audit-1'")) {
            assertTrue(rows.next()); assertEquals("operator",rows.getString(1)); assertEquals("SUCCEEDED",rows.getString(2));
        }
    }

    @Test
    void alertTransitionAndDispositionAreWrittenTogether() throws Exception {
        assertTrue(repository.transitionAlert("system-a","alert-a",1,"ACKNOWLEDGED","operator","triaged","ack-1"));
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement();
            ResultSet rows=statement.executeQuery("SELECT disposition_type,operator_id FROM alert_disposition WHERE disposition_id='ack-1'")) {
            assertTrue(rows.next()); assertEquals("ACKNOWLEDGED",rows.getString(1)); assertEquals("operator",rows.getString(2));
        }
    }
}
