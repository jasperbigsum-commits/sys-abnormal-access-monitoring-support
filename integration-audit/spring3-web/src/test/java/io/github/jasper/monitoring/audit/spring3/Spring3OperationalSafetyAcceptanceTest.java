package io.github.jasper.monitoring.audit.spring3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.jasper.monitoring.audit.spring3.persistence.AuditFixtureRepository;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:audit-spring3-safety;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ExtendWith(OutputCaptureExtension.class)
class Spring3OperationalSafetyAcceptanceTest {
    @LocalServerPort private int port;
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private AuditFixtureRepository fixtures;

    @Test
    @DisplayName("TC-13 duplicate controls execute one side effect without extending TTL")
    void tc13_duplicateControlsExecuteOneSideEffectWithoutExtendingTtl() throws Exception {
        fixtures.createSession("tc13-session", "tc11-user", Instant.now());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("idempotencyKey", "tc13-revoke-once");
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> calls = new ArrayList<Callable<Integer>>();
            for (int index = 0; index < 6; index++) {
                calls.add(() -> postJson("/audit/management/sessions/tc11-user/revoke", "audit-admin", body)
                    .getStatusCode().value());
            }
            for (Future<Integer> result : executor.invokeAll(calls)) {
                assertEquals(HttpStatus.OK.value(), result.get().intValue());
            }
        } finally {
            executor.shutdownNow();
        }
        java.sql.Timestamp expires = jdbc.queryForObject("SELECT expires_at FROM audit_control_state "
            + "WHERE idempotency_key='tc13-revoke-once'", java.sql.Timestamp.class);
        assertEquals(HttpStatus.OK,
            postJson("/audit/management/sessions/tc11-user/revoke", "audit-admin", body).getStatusCode());
        assertEquals(1L, jdbc.queryForObject("SELECT execution_count FROM audit_control_state "
            + "WHERE idempotency_key='tc13-revoke-once'", Long.class).longValue());
        assertEquals(expires, jdbc.queryForObject("SELECT expires_at FROM audit_control_state "
            + "WHERE idempotency_key='tc13-revoke-once'", java.sql.Timestamp.class));
        assertEquals(0L, fixtures.countActiveSessions("tc11-user"));
    }

    @Test
    @DisplayName("TC-15 sensitive values never reach HTTP audit storage or logs")
    void tc15_sensitiveValuesNeverReachHttpAuditStorageOrLogs(CapturedOutput output) throws Exception {
        String[] secrets = {"Password-TC15-9f7a", "Bearer-TC15-a81c", "Cookie-TC15-b72d",
            "ApiKey-TC15-c63e"};
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("userId", "tc11-user");
        body.put("accepted", Boolean.FALSE);
        body.put("password", secrets[0]);
        body.put("apiKey", secrets[3]);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + secrets[1]);
        headers.set("Cookie", "SESSION=" + secrets[2]);
        ResponseEntity<String> response = http.exchange("http://localhost:" + port + "/audit/authentication/login",
            HttpMethod.POST, new HttpEntity<Map<String, Object>>(body, headers), String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        for (String secret : secrets) {
            assertFalse(response.getBody().contains(secret));
            assertEquals(0L, countSensitiveColumns(secret));
            assertFalse(output.getAll().contains(secret));
        }
    }

    private long countSensitiveColumns(String secret) throws Exception {
        long matches = 0L;
        try (java.sql.Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet columns = metadata.getColumns(null, "PUBLIC", "%", "%")) {
                while (columns.next()) {
                    int type = columns.getInt("DATA_TYPE");
                    String table = columns.getString("TABLE_NAME");
                    if (!isText(type) || !isAuditTable(table)) continue;
                    String column = columns.getString("COLUMN_NAME");
                    matches += jdbc.queryForObject("SELECT COUNT(*) FROM \"" + table + "\" WHERE CAST(\""
                        + column + "\" AS VARCHAR) LIKE ?", Long.class, "%" + secret + "%").longValue();
                }
            }
        }
        return matches;
    }

    private static boolean isText(int type) {
        return type == java.sql.Types.VARCHAR || type == java.sql.Types.CHAR
            || type == java.sql.Types.LONGVARCHAR || type == java.sql.Types.CLOB;
    }

    private static boolean isAuditTable(String table) {
        return table.startsWith("SECURITY_") || table.startsWith("CONTROL_")
            || table.startsWith("ALERT_") || table.startsWith("MANAGEMENT_") || table.startsWith("AUDIT_");
    }

    private ResponseEntity<String> postJson(String path, String principal, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (principal != null) headers.set("X-Audit-Principal", principal);
        return http.exchange("http://localhost:" + port + path, HttpMethod.POST,
            new HttpEntity<Map<String, Object>>(body, headers), String.class);
    }
}
