package io.github.jasper.monitoring.audit.spring2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:audit-spring2-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
class Spring2ManagementApiContractTest {
    @LocalServerPort private int port;
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void eventsUsesJeecgEnvelopeAndPreservesZeroBasedPagination() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/audit/management/events?page=0&size=1", "audit-admin", null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"success\":true"));
        assertTrue(response.getBody().contains("\"result\""));
        assertTrue(response.getBody().contains("\"page\":0"));
        assertTrue(response.getBody().contains("\"size\":1"));
        assertTrue(response.getBody().contains("\"totalElements\""));
    }

    @Test
    void dashboardUsesJeecgEnvelopeAndFrontendSummaryShape() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/audit/management/dashboard", "audit-admin", null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"success\":true"));
        assertTrue(response.getBody().contains("\"metrics\""));
        assertTrue(response.getBody().contains("\"riskDistribution\""));
        assertTrue(response.getBody().contains("\"priorityAlertIds\""));
    }

    @Test
    void managementAuditUsesJeecgEnvelopeAndPagination() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/audit/management/audit-log?page=0&size=20", "audit-admin", null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"success\":true"));
        assertTrue(response.getBody().contains("\"items\""));
        assertTrue(response.getBody().contains("\"totalElements\""));
    }

    @Test
    void debugSessionUsesTheSameAuthorizedEnvelope() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/audit/management/debug/session", "audit-admin", null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"success\":true"));
        assertTrue(response.getBody().contains("\"contract\":\"jeecg-management-v1\""));
    }

    @Test
    void falsePositivePathUsesJeecgEnvelope() {
        jdbc.update("INSERT INTO audit_account(user_id,organization_id,status) VALUES(?,?,?)", "api-alert", "org-a", "ACTIVE");
        for (int attempt = 0; attempt < 5; attempt++) exchange(HttpMethod.POST, "/audit/login-failure", "api-alert", null);
        String alertId = jdbc.queryForObject("SELECT alert_id FROM monitoring_security_alert "
            + "WHERE rule_id='AUTH-01' AND subject LIKE 'v1:%'", String.class);
        long version = jdbc.queryForObject("SELECT version FROM monitoring_security_alert WHERE alert_id=?", Long.class, alertId).longValue();
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("expectedVersion", Long.valueOf(version)); request.put("reason", "frontend false positive"); request.put("idempotencyKey", "api-false-positive");
        ResponseEntity<String> response = exchange(HttpMethod.POST,
            "/audit/management/alerts/" + alertId + "/false-positive", "audit-admin", request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"success\":true"));
        assertTrue(response.getBody().contains("\"status\":\"FALSE_POSITIVE\""));
    }

    @Test
    void deniedRequestUsesJeecgErrorEnvelope() {
        ResponseEntity<String> response = exchange(HttpMethod.GET, "/audit/management/events?page=0&size=20", "audit-viewer", null);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().contains("\"success\":false"));
        assertTrue(response.getBody().contains("\"code\":403"));
        assertTrue(response.getBody().contains("\"errorType\":\"FORBIDDEN\""));
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String principal, Object body) {
        HttpHeaders headers = new HttpHeaders(); headers.set("X-Audit-Principal", principal);
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange("http://localhost:" + port + path, method, new HttpEntity<Object>(body, headers), String.class);
    }
}
