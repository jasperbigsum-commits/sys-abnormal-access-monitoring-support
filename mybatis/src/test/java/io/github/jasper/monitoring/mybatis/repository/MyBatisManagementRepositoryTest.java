package io.github.jasper.monitoring.mybatis.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementOperation;
import io.github.jasper.monitoring.api.management.ManagementPageRequest;
import io.github.jasper.monitoring.api.management.query.SecurityEventQuery;
import io.github.jasper.monitoring.api.management.query.AlertAssignmentQuery;
import io.github.jasper.monitoring.api.rule.RuleMode;
import io.github.jasper.monitoring.core.domain.management.ManagementAuditRecord;
import io.github.jasper.monitoring.mybatis.MyBatisMonitoringStoreRegistrar;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
            statement.execute("CREATE TABLE alert_disposition(disposition_id VARCHAR(128) PRIMARY KEY,alert_id VARCHAR(128),disposition_type VARCHAR(64),operator_id VARCHAR(128),assignee_id VARCHAR(128),expected_version BIGINT,comment_text VARCHAR(1024),evidence_summary VARCHAR(1024),created_at TIMESTAMP)");
            statement.execute("CREATE TABLE security_rule(system_id VARCHAR(128),rule_id VARCHAR(128),rule_version BIGINT,rule_name VARCHAR(256),rule_definition CLOB,risk_level VARCHAR(32),rule_mode VARCHAR(32),rule_threshold BIGINT,enabled BOOLEAN,created_at TIMESTAMP,created_by VARCHAR(128),change_reason VARCHAR(512),approved_by VARCHAR(128),idempotency_key VARCHAR(128),UNIQUE(system_id,idempotency_key),PRIMARY KEY(system_id,rule_id,rule_version))");
            statement.execute("INSERT INTO security_event VALUES('event-a','system-a',TIMESTAMP '2026-07-20 00:00:00','report:export','SUCCESS')");
            statement.execute("INSERT INTO security_event VALUES('event-b','system-b',TIMESTAMP '2026-07-20 00:00:00','report:export','SUCCESS')");
            statement.execute("INSERT INTO security_alert VALUES('alert-a','NEW',0,TIMESTAMP '2026-07-20 00:00:00')");
            statement.execute("INSERT INTO alert_event_link VALUES('alert-a','event-a')");
            statement.execute("INSERT INTO security_rule VALUES('system-a','rule-a',1,'Rule A','{}','HIGH','ALERT_ONLY',2,TRUE,CURRENT_TIMESTAMP,'system',NULL,NULL,NULL)");
            statement.execute("INSERT INTO security_rule VALUES('system-b','rule-a',1,'Rule B','{}','LOW','OBSERVE',9,TRUE,CURRENT_TIMESTAMP,'system',NULL,NULL,NULL)");
        }
        Configuration configuration=new Configuration(new Environment("test",new JdbcTransactionFactory(),dataSource));
        MyBatisMonitoringStoreRegistrar.register(configuration);
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
        assertTrue(repository.transitionAlert("system-a","alert-a",0,"ACKNOWLEDGED","operator","triaged","ack-1"));
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement();
            ResultSet rows=statement.executeQuery("SELECT disposition_type,operator_id FROM alert_disposition WHERE disposition_id='ack-1'")) {
            assertTrue(rows.next()); assertEquals("ACKNOWLEDGED",rows.getString(1)); assertEquals("operator",rows.getString(2));
        }
    }

    @Test
    void alertAssignmentAdvancesVersionAndAppendsAssignee() throws Exception {
        assertTrue(repository.assignAlert("system-a","alert-a",0,"operator","analyst-1","triage","assign-1"));
        assertFalse(repository.assignAlert("system-a","alert-a",0,"operator","analyst-2","stale","assign-2"));
        assertEquals("IN_PROGRESS", repository.findAlertView("system-a", "alert-a").get().getStatus());
        assertEquals("analyst-1", repository.findAlertView("system-a", "alert-a").get().getAssigneeId());
        assertTrue(repository.findAlertAssignment("system-a","alert-a",0,"operator","analyst-1","triage",
            "assign-1").isPresent());
        assertFalse(repository.findAlertAssignment("system-a","alert-a",0,"operator","analyst-2","triage",
            "assign-1").isPresent());
        assertFalse(repository.findAlertAssignment("system-a","alert-a",0,"operator","ANALYST-1","triage",
            "assign-1").isPresent());
        io.github.jasper.monitoring.api.management.ManagementPageRequest historyPage =
            ManagementPageRequest.of(0,20,AlertAssignmentQuery.Sort.CREATED_AT);
        assertEquals(1,repository.searchAlertAssignments("system-a","alert-a",
            AlertAssignmentQuery.of(historyPage)).getItems().size());
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement();
            ResultSet rows=statement.executeQuery("SELECT assignee_id,operator_id FROM alert_disposition WHERE disposition_id='assign-1'")) {
            assertTrue(rows.next()); assertEquals("analyst-1",rows.getString(1)); assertEquals("operator",rows.getString(2));
        }
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement()) {
            statement.executeUpdate("UPDATE security_alert SET status='CLOSED',version=3 WHERE alert_id='alert-a'");
        }
        assertFalse(repository.assignAlert("system-a","alert-a",3,"operator","analyst-2","reopen","assign-3"));
    }

    @Test
    void ruleChangeAppendsVersionAndRejectsStaleVersion() throws Exception {
        assertTrue(repository.changeRule("system-a","rule-a",1,RuleMode.ENFORCE,7,"operator","approver",
            "approved change","rule-change-1"));
        assertFalse(repository.changeRule("system-a","rule-a",1,RuleMode.ALERT_ONLY,3,"operator","approver",
            "stale","rule-change-2"));
        assertEquals(2,repository.findRuleView("system-a","rule-a").get().getVersion());
        assertEquals(RuleMode.ENFORCE,repository.findRuleView("system-a","rule-a").get().getMode());
        assertEquals(7,repository.findRuleView("system-a","rule-a").get().getThreshold());
        assertEquals(1,repository.findRuleView("system-b","rule-a").get().getVersion());
        assertEquals(RuleMode.OBSERVE,repository.findRuleView("system-b","rule-a").get().getMode());
        assertTrue(repository.findRuleChange("system-a","rule-a",1,RuleMode.ENFORCE,7,"operator","approver",
            "approved change","rule-change-1").isPresent());
        assertFalse(repository.findRuleChange("system-b","rule-a",1,RuleMode.ENFORCE,7,"operator","approver",
            "approved change","rule-change-1").isPresent());
        assertFalse(repository.findRuleChange("system-a","rule-a",1,RuleMode.ENFORCE,7,"operator","approver",
            "APPROVED CHANGE","rule-change-1").isPresent());
        try(Connection connection=dataSource.getConnection();Statement statement=connection.createStatement();
            ResultSet rows=statement.executeQuery("SELECT COUNT(*) FROM security_rule WHERE system_id='system-a' AND rule_id='rule-a'")) {
            assertTrue(rows.next()); assertEquals(2,rows.getInt(1));
        }
    }

    @Test
    void concurrentRuleChangesProduceOneWinnerAndOneConflict() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> {
                start.await();
                return repository.changeRule("system-a","rule-a",1,RuleMode.ENFORCE,7,"operator-1",
                    "approver-1","first","race-1");
            });
            Future<Boolean> second = pool.submit(() -> {
                start.await();
                return repository.changeRule("system-a","rule-a",1,RuleMode.OBSERVE,8,"operator-2",
                    "approver-2","second","race-2");
            });
            start.countDown();

            int winners = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertEquals(1, winners);
        } finally {
            pool.shutdownNow();
        }
    }
}
