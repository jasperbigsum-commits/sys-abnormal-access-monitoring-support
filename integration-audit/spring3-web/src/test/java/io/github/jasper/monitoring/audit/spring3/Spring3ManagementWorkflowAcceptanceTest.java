package io.github.jasper.monitoring.audit.spring3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Boot 3 的安全管理工作流验收测试。
 *
 * <p>覆盖白名单有效期、规则双人审批与版本追加、告警分派和处置时间线，重点验证乐观锁、
 * 系统范围授权和仅追加审计记录。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:audit-spring3-management;MODE=MySQL;DB_CLOSE_DELAY=-1")
class Spring3ManagementWorkflowAcceptanceTest {
    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate http;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("TC-12 whitelist suppresses a rule only while active and unexpired")
    void tc12_whitelistSuppressesRuleOnlyWhileActiveAndUnexpired() {
        jdbc.update("INSERT INTO audit_account(user_id,organization_id,status) VALUES(?,?,?)",
            "tc12-whitelist", "org-a", "ACTIVE");
        assertEquals(HttpStatus.OK, post("/audit/login-failure", "tc12-whitelist").getStatusCode());
        String subjectKey = latestLoginSubjectKey();
        jdbc.update("INSERT INTO monitoring_security_whitelist(whitelist_id,system_id,rule_id,subject,reason,"
                + "approved_by,expires_at,created_at,status,version) VALUES(?,?,?,?,?,?,?,?,?,?)",
            "tc12", "audit-spring3-web", "AUTH-01", subjectKey, "predeclared", "fixture",
            java.sql.Timestamp.from(Instant.now().plusSeconds(3600)), java.sql.Timestamp.from(Instant.now()),
            "REVOKED", Long.valueOf(1L));

        ResponseEntity<String> granted = postJson("/audit/management/whitelists/tc12/grant", "audit-admin",
            versioned(1L, "approved for maintenance"));
        assertEquals(HttpStatus.OK, granted.getStatusCode());
        assertTrue(granted.getBody().contains("\"status\":\"ACTIVE\""));
        for (int attempt = 1; attempt < 5; attempt++) {
            assertEquals(HttpStatus.OK, post("/audit/login-failure", "tc12-whitelist").getStatusCode());
        }
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_security_alert "
            + "WHERE rule_id='AUTH-01' AND subject=?", Long.class, subjectKey).longValue());

        jdbc.update("UPDATE monitoring_security_whitelist SET expires_at=? WHERE whitelist_id='tc12'",
            java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
        assertEquals(HttpStatus.OK, post("/audit/login-failure", "tc12-whitelist").getStatusCode());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM monitoring_security_alert "
            + "WHERE rule_id='AUTH-01' AND subject=?", Long.class, subjectKey).longValue());
        assertTrue(count("SELECT COUNT(*) FROM monitoring_management_audit WHERE target_id='tc12' "
            + "AND action='WHITELIST_GRANT' AND outcome='SUCCEEDED'") >= 1L);
    }

    @Test
    @DisplayName("TC-16 rule updates append approved versions and reject stale writes")
    void tc16_ruleUpdatesAppendApprovedVersionsAndRejectStaleWrites() {
        seedRule();
        Map<String, Object> change = ruleChange(1L, "OBSERVE", 7L, "lower false positives", "tc16-change");
        assertEquals(HttpStatus.FORBIDDEN,
            postJson("/audit/management/rules/AUTH-01/versions", "audit-admin", change).getStatusCode());
        assertEquals(1L, count("SELECT COUNT(*) FROM monitoring_security_rule WHERE rule_id='AUTH-01'"));
        ResponseEntity<String> changed = postApprovedJson("/audit/management/rules/AUTH-01/versions",
            "audit-admin", "audit-approver", change);
        assertEquals(HttpStatus.OK, changed.getStatusCode());
        assertTrue(changed.getBody().contains("\"version\":2"));
        assertEquals(2L, count("SELECT COUNT(*) FROM monitoring_security_rule WHERE rule_id='AUTH-01'"));
        assertEquals("audit-admin", jdbc.queryForObject("SELECT created_by FROM monitoring_security_rule "
            + "WHERE rule_id='AUTH-01' AND rule_version=2", String.class));
        assertEquals("audit-approver", jdbc.queryForObject("SELECT approved_by FROM monitoring_security_rule "
            + "WHERE rule_id='AUTH-01' AND rule_version=2", String.class));
        assertEquals("OBSERVE", jdbc.queryForObject("SELECT rule_mode FROM monitoring_security_rule "
            + "WHERE rule_id='AUTH-01' AND rule_version=2", String.class));
        assertEquals(7L, jdbc.queryForObject("SELECT rule_threshold FROM monitoring_security_rule "
            + "WHERE rule_id='AUTH-01' AND rule_version=2", Long.class).longValue());
        assertTrue(jdbc.queryForObject("SELECT created_at FROM monitoring_security_rule "
            + "WHERE rule_id='AUTH-01' AND rule_version=2", java.sql.Timestamp.class) != null);

        ResponseEntity<String> stale = postApprovedJson("/audit/management/rules/AUTH-01/versions", "audit-admin",
            "audit-approver", ruleChange(1L, "ALERT_ONLY", 9L, "stale", "tc16-stale"));
        assertEquals(HttpStatus.CONFLICT, stale.getStatusCode());
        assertEquals(2L, count("SELECT COUNT(*) FROM monitoring_security_rule WHERE rule_id='AUTH-01'"));
    }

    @Test
    @DisplayName("TC-18 alert lifecycle is versioned append-only and permission scoped")
    void tc18_alertLifecycleIsVersionedAppendOnlyAndPermissionScoped() {
        jdbc.update("INSERT INTO audit_account(user_id,organization_id,status) VALUES(?,?,?)",
            "tc18-alert", "org-a", "ACTIVE");
        for (int attempt = 0; attempt < 5; attempt++) {
            post("/audit/login-failure", "tc18-alert");
        }
        String subjectKey = latestLoginSubjectKey();
        String alertId = jdbc.queryForObject("SELECT alert_id FROM monitoring_security_alert WHERE rule_id='AUTH-01' "
            + "AND subject=?", String.class, subjectKey);
        long version = jdbc.queryForObject("SELECT version FROM monitoring_security_alert WHERE alert_id=?", Long.class,
            alertId).longValue();
        assertEquals(HttpStatus.OK, postJson("/audit/management/alerts/" + alertId + "/acknowledge",
            "audit-admin", alert(version, "acknowledged", "tc18-ack", null)).getStatusCode());
        version++;
        assertEquals(HttpStatus.OK, postJson("/audit/management/alerts/" + alertId + "/assign",
            "audit-admin", alert(version, "investigate", "tc18-assign", "operator-a")).getStatusCode());
        version++;
        assertEquals(HttpStatus.OK, postJson("/audit/management/alerts/" + alertId + "/investigate",
            "audit-admin", alert(version, "evidence reviewed", "tc18-investigate", null)).getStatusCode());
        version++;
        assertEquals(HttpStatus.CONFLICT, postJson("/audit/management/alerts/" + alertId + "/close",
            "audit-admin", alert(version - 1L, "stale close", "tc18-stale", null)).getStatusCode());
        assertEquals(3L, count("SELECT COUNT(*) FROM monitoring_alert_disposition WHERE alert_id='" + alertId + "'"));
        ResponseEntity<String> closed = postJson("/audit/management/alerts/" + alertId + "/close",
            "audit-admin", alert(version, "resolved", "tc18-close", null));
        assertEquals(HttpStatus.OK, closed.getStatusCode());
        assertTrue(closed.getBody().contains("\"status\":\"CLOSED\""));
        assertEquals(4L, count("SELECT COUNT(*) FROM monitoring_alert_disposition WHERE alert_id='" + alertId + "'"));
        assertEquals(1L, count("SELECT COUNT(*) FROM monitoring_alert_disposition WHERE alert_id='" + alertId
            + "' AND disposition_type='ACKNOWLEDGED' AND comment_text='acknowledged'"));
        assertEquals(HttpStatus.FORBIDDEN, postJson("/audit/management/alerts/" + alertId + "/close",
            "audit-viewer", alert(version + 1L, "unauthorized", "tc18-denied", null)).getStatusCode());
        assertEquals(4L, count("SELECT COUNT(*) FROM monitoring_alert_disposition WHERE alert_id='" + alertId + "'"));
    }

    private void seedRule() {
        jdbc.update("INSERT INTO monitoring_security_rule(system_id,rule_id,rule_version,rule_name,rule_definition,"
                + "risk_level,rule_mode,rule_threshold,enabled,created_at,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            "audit-spring3-web", "AUTH-01", Integer.valueOf(1), "login failures", "{}", "HIGH", "ENFORCE",
            Long.valueOf(5L), Boolean.TRUE, java.sql.Timestamp.from(Instant.now()), "fixture");
    }

    private ResponseEntity<String> post(String path, String principal) {
        return http.exchange(url(path), HttpMethod.POST, new HttpEntity<Void>(headers(principal)), String.class);
    }
    private ResponseEntity<String> postJson(String path, String principal, Map<String, Object> body) {
        HttpHeaders headers = headers(principal); headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url(path), HttpMethod.POST, new HttpEntity<Map<String, Object>>(body, headers),
            String.class);
    }

    private ResponseEntity<String> postApprovedJson(String path, String principal, String approver,
                                                     Map<String, Object> body) {
        HttpHeaders headers = headers(principal);
        headers.set("X-Audit-Approver", approver);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url(path), HttpMethod.POST, new HttpEntity<Map<String, Object>>(body, headers),
            String.class);
    }
    private HttpHeaders headers(String principal) {
        HttpHeaders headers = new HttpHeaders(); headers.set("X-Audit-Principal", principal); return headers;
    }
    private String url(String path) { return "http://localhost:" + port + path; }
    private long count(String sql) { return jdbc.queryForObject(sql, Long.class).longValue(); }
    private String latestLoginSubjectKey() {
        return jdbc.queryForObject("SELECT f.value_text FROM monitoring_security_event_fact f "
            + "JOIN monitoring_security_event e ON e.event_id=f.event_id "
            + "WHERE f.fact_key='login_subject_key' AND e.event_type='LOGIN_FAILURE' "
            + "AND e.reason_code='MON.AUTH.INVALID_CREDENTIAL' ORDER BY e.occurred_at DESC LIMIT 1", String.class);
    }
    private static Map<String, Object> versioned(long version, String reason) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("expectedVersion", Long.valueOf(version)); body.put("reason", reason); return body;
    }
    private static Map<String, Object> ruleChange(long version, String mode, long threshold, String reason, String key) {
        Map<String, Object> body = versioned(version, reason); body.put("mode", mode);
        body.put("threshold", Long.valueOf(threshold)); body.put("idempotencyKey", key); return body;
    }
    private static Map<String, Object> alert(long version, String reason, String key, String assignee) {
        Map<String, Object> body = versioned(version, reason); body.put("idempotencyKey", key);
        if (assignee != null) body.put("assigneeId", assignee); return body;
    }
}
