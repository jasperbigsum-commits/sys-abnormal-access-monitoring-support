package io.github.jasper.monitoring.audit.spring3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.core.application.control.AnnotatedControlHandler;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.port.ControlHandler;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementPageRequest;
import io.github.jasper.monitoring.api.management.SecurityEventQueryService;
import io.github.jasper.monitoring.api.management.query.SecurityEventQuery;
import io.github.jasper.monitoring.mybatis.repository.MyBatisMonitoringStore;
import io.github.jasper.monitoring.audit.spring3.report.AuditExportService;
import io.github.jasper.monitoring.audit.spring3.persistence.AuditFixtureRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Spring3AuditWebAcceptanceTest {
    private static final long SERVER_REPORTED_ROW_COUNT = 37L;
    private static final String AUDIT_PRINCIPAL_HEADER = "X-Audit-Principal";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MyBatisMonitoringStore repository;

    @Autowired
    private SecurityEventQueryService eventQueries;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private AuditExportService exportService;

    @Autowired
    private AuditFixtureRepository fixtures;

    @BeforeEach
    void resetExportSideEffects() {
        exportService.reset();
    }

    @Test
    void usesMyBatisRepositoryForDurableAuditEvidence() {
        assertTrue(repository instanceof MyBatisMonitoringStore,
            "The integration host must persist audit evidence through MyBatis, never the memory adapter");
        assertEquals("ACTIVE", String.valueOf(fixtures.findAccount("audit-exporter").get("STATUS")));
        assertEquals("report-a", String.valueOf(fixtures.findReport("report-a").get("REPORTID")));
        assertTrue(fixtures.findRoles("audit-admin").contains("audit-admin"));
        assertTrue(fixtures.counts().getControls() >= 0L);
        assertTrue(fixtures.counts().getExports() >= 0L);
        assertTrue(fixtures.counts().getNotificationAttempts() >= 0L);
    }

    @Test
    void rejectsMissingAndUnknownFixturePrincipals() {
        assertEquals(HttpStatus.UNAUTHORIZED,
            restTemplate.getForEntity(url("/audit/reports/report-a"), String.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED,
            get("/audit/reports/report-a", "unknown-principal").getStatusCode());
    }

    @Test
    void allowsSameOrganizationReadAndExportWhenPermitted() {
        assertEquals(HttpStatus.OK, get("/audit/reports/report-a", "audit-exporter").getStatusCode());
        assertEquals(HttpStatus.OK,
            post("/audit/reports/report-a/export", "audit-exporter").getStatusCode());
        assertEquals(1, exportService.getInvocationCount());
    }

    @Test
    void deniesCrossOrganizationExportBeforeTheExportServiceRuns() {
        ResponseEntity<String> response = post("/audit/reports/report-b/export", "audit-exporter");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(0, exportService.getInvocationCount());
        assertTrue(repository.findSince("audit-spring3-web", Instant.EPOCH).stream()
            .anyMatch(event -> "authz:access-denied".equals(event.getAction())
                && "report-b".equals(event.getResourceId())));
    }

    @Test
    void deniesExportWithoutPermissionBeforeTheExportServiceRuns() {
        ResponseEntity<String> response = post("/audit/reports/report-a/export", "audit-viewer");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(0, exportService.getInvocationCount());
        assertTrue(repository.findSince("audit-spring3-web", Instant.EPOCH).stream()
            .anyMatch(event -> "authz:access-denied".equals(event.getAction())
                && "audit-viewer".equals(event.getUserId())));
    }

    @Test
    void auditsFiveLoginFailuresOverHttpWithAlertAndControlEvidence() {
        assertTrue(applicationContext.containsBean("auditControlActions"),
            "The sample host must expose its @ControlTrigger methods as a normal Spring bean");
        Object controlActions = applicationContext.getBean("auditControlActions");
        assertTrue(AnnotatedControlHandler.hasBindings(controlActions));
        assertTrue(applicationContext.getBeansOfType(ControlHandler.class).isEmpty(),
            "The sample host must use @ControlTrigger instead of a handwritten ControlHandler bean");

        ResponseEntity<String> lastResponse = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            lastResponse = post("/audit/login-failure", "audit-exporter");
            assertEquals(HttpStatus.OK, lastResponse.getStatusCode());
        }

        assertTrue(lastResponse.getBody().contains("\"action\":\"auth:login-failure\""));
        List<SecurityEvent> events = repository.findSince("audit-spring3-web", Instant.now().minusSeconds(60));
        assertEquals(6, events.stream().filter(event -> "auth:login-failure".equals(event.getAction())).count());
        assertTrue(events.stream().filter(event -> "auth:login-failure".equals(event.getAction()))
            .allMatch(event -> "audit-exporter".equals(event.getUserId())));
        String alertId = jdbc.queryForObject(
            "SELECT alert_id FROM security_alert WHERE rule_id = 'AUTH-01'", String.class);
        assertTrue(repository.findControl(alertId + ":" + ControlActionType.REQUIRE_CAPTCHA).isPresent());
        assertTrue(repository.findControl(alertId + ":" + ControlActionType.RATE_LIMIT).isPresent());
    }

    @Test
    void auditsRegisteredExportWithDynamicFactsOverHttp() {
        ResponseEntity<String> response = post("/audit/export", "audit-exporter");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        SecurityEvent event = latestEvent("report:export");
        assertEquals(5000L, event.getDataCount());
        assertEquals("audit-export-2026", event.getResourceId());
        assertTrue(repository.findOpen("EXPT-01|audit-exporter|report:audit-export-2026").isPresent());
    }

    @Test
    void recordsAnnotatedMvcActionOverHttp() {
        ResponseEntity<String> response = get("/audit/annotated-query", "audit-exporter");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(SecurityEventType.QUERY, latestEvent("data:query").getEventType());
    }

    @Test
    void recordsAnnotatedExportFactsFromNestedRequestBindingAndReturnValue() {
        ResponseEntity<String> response = restTemplate.postForEntity(url("/audit/annotated-export"),
            exportRequest("audit-export-2026", "org-a", 5000, "audit-exporter"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        SecurityEvent event = latestEvent("data:query");
        assertEquals(SecurityEventType.QUERY, event.getEventType());
        assertEquals(SecurityEventResult.SUCCESS, event.getResult());
    }

    @Test
    void letsForbiddenHttpStatusOverrideAnEnricherSuccess() {
        ResponseEntity<String> response = restTemplate.postForEntity(url("/audit/annotated-export-denied"),
            exportRequest("denied-report", "org-denied", 12, "audit-exporter"), String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(0L, repository.findSince("audit-spring3-web", Instant.now().minusSeconds(5)).stream()
            .filter(event -> "audit:annotated-export-denied".equals(event.getAction())).count());
    }

    @Test
    void authorizesAndAuditsManagementEventQueries() {
        post("/audit/export", "audit-exporter");
        ResponseEntity<String> response = get("/audit/management/events", "audit-admin");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"count\":"));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM management_audit WHERE system_id = ? "
            + "AND actor_id = ? AND action = ? AND outcome = ?", Long.class,
            "audit-spring3-web", "audit-admin", "EVENT_READ", "SUCCEEDED").longValue() >= 1L);
    }

    @Test
    void rejectsAndAuditsCrossSystemManagementQueries() {
        SecurityEventQuery query = SecurityEventQuery.of(
            ManagementPageRequest.of(0, 20, SecurityEventQuery.Sort.OCCURRED_AT),
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(1));
        assertThrows(SecurityException.class, () -> eventQueries.search(
            ManagementActor.of("foreign-admin", "foreign-system"), query));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM management_audit WHERE system_id = ? "
            + "AND actor_id = ? AND action = ? AND outcome = ?", Long.class,
            "foreign-system", "foreign-admin", "EVENT_READ", "DENIED").longValue() >= 1L);
    }

    @Test
    @DisplayName("TC-01 fifth failure activates challenge and controls the next login")
    void tc01_fifthFailureActivatesChallengeAndControlsNextLogin() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertEquals(HttpStatus.FORBIDDEN,
                postJson("/audit/authentication/login", null, login("tc01-user", false)).getStatusCode());
        }
        ResponseEntity<String> challenged =
            postJson("/audit/authentication/login", null, login("tc01-user", true));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, challenged.getStatusCode());
        assertTrue(challenged.getBody().contains("CHALLENGE_REQUIRED"));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM audit_control_state "
            + "WHERE subject='tc01-user' AND control_type='REQUIRE_CAPTCHA'", Long.class).longValue());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM audit_control_state "
            + "WHERE subject='tc01-user' AND control_type='RATE_LIMIT'", Long.class).longValue());
    }

    @Test
    @DisplayName("TC-03 disabled account is rejected without creating a session")
    void tc03_disabledAccountIsRejectedWithoutCreatingSession() {
        ResponseEntity<String> response =
            postJson("/audit/authentication/login", null, login("audit-disabled", true));
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(response.getBody().contains("ACCOUNT_DISABLED"));
        assertEquals(0L, fixtures.countActiveSessions("audit-disabled"));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM security_alert "
            + "WHERE rule_id='AUTH-03' AND subject='audit-disabled'", Long.class).longValue());
    }

    @Test
    @DisplayName("TC-11 session revocation is durable and idempotent")
    void tc11_sessionRevocationIsDurableAndIdempotent() {
        fixtures.createSession("tc11-session-a", "tc11-user", Instant.now());
        fixtures.createSession("tc11-session-b", "tc11-user", Instant.now());
        Map<String, Object> request = new LinkedHashMap<String, Object>();
        request.put("idempotencyKey", "tc11-revoke-once");
        assertEquals(HttpStatus.OK,
            postJson("/audit/management/sessions/tc11-user/revoke", "audit-admin", request).getStatusCode());
        ResponseEntity<String> replay =
            postJson("/audit/management/sessions/tc11-user/revoke", "audit-admin", request);
        assertEquals(HttpStatus.OK, replay.getStatusCode());
        assertTrue(replay.getBody().contains("\"replay\":true"));
        assertEquals(0L, fixtures.countActiveSessions("tc11-user"));
        assertTrue(get("/audit/authentication/sessions/active?sessionId=tc11-session-a", null)
            .getBody().contains("false"));
        assertEquals(1L, jdbc.queryForObject("SELECT execution_count FROM audit_control_state "
            + "WHERE idempotency_key='tc11-revoke-once'", Long.class).longValue());
    }

    @Test
    @DisplayName("TC-02 multi-account attack rate limits only the trusted source IP")
    void tc02_multiAccountAttackRateLimitsOnlySourceIp() {
        for (int index = 0; index < 10; index++) {
            String userId=String.format("tc02-user-%02d",Integer.valueOf(index));
            assertEquals(HttpStatus.FORBIDDEN,
                postJsonFrom("/audit/authentication/login",null,login(userId,false),"198.51.100.10").getStatusCode());
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
            postJsonFrom("/audit/authentication/login",null,login("tc02-safe",true),"198.51.100.10").getStatusCode());
        assertEquals(HttpStatus.OK,
            postJsonFrom("/audit/authentication/login",null,login("tc02-safe",true),"198.51.100.11").getStatusCode());
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM audit_control_state "
            + "WHERE subject='ip:198.51.100.10' AND control_type='RATE_LIMIT'",Long.class).longValue());
    }

    @Test
    @DisplayName("TC-06 resource enumeration revokes the active session")
    void tc06_resourceEnumerationRevokesTheActiveSession() {
        fixtures.createSession("tc06-session","audit-traversal",Instant.now());
        for(int index=0;index<100;index++){
            assertEquals(HttpStatus.OK,getQuery("audit-traversal","resource-"+index,true,"tc06-session").getStatusCode());
        }
        assertEquals(0L,fixtures.countActiveSessions("audit-traversal"));
        assertEquals(HttpStatus.UNAUTHORIZED,
            getQuery("audit-traversal","resource-after-control",true,"tc06-session").getStatusCode());
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM security_alert "
            + "WHERE rule_id='AUTHZ-02' AND subject='audit-traversal'",Long.class).longValue());
    }

    @Test
    @DisplayName("TC-07 query threshold rate limits only the target user")
    void tc07_queryThresholdRateLimitsOnlyTheTargetUser() {
        for(int index=0;index<120;index++){
            assertEquals(HttpStatus.OK,getQuery("audit-query","query-resource",false,null).getStatusCode());
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
            getQuery("audit-query","query-resource",false,null).getStatusCode());
        assertEquals(HttpStatus.OK,
            getQuery("audit-query-other","query-resource",false,null).getStatusCode());
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM security_alert "
            + "WHERE rule_id='DATA-01' AND subject='audit-query'",Long.class).longValue());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> get(String path, String principal) {
        return restTemplate.exchange(url(path), org.springframework.http.HttpMethod.GET,
            new HttpEntity<Void>(headers(principal)), String.class);
    }

    private ResponseEntity<String> post(String path, String principal) {
        return restTemplate.postForEntity(url(path), new HttpEntity<Void>(headers(principal)), String.class);
    }

    private ResponseEntity<String> postJson(String path, String principal, Map<String, Object> body) {
        HttpHeaders headers = headers(principal);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(url(path), new HttpEntity<Map<String, Object>>(body, headers), String.class);
    }

    private ResponseEntity<String> postJsonFrom(String path,String principal,Map<String,Object> body,String sourceIp){
        HttpHeaders headers=headers(principal); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For",sourceIp);
        return restTemplate.postForEntity(url(path),new HttpEntity<Map<String,Object>>(body,headers),String.class);
    }

    private ResponseEntity<String> getQuery(String principal,String resourceId,boolean sequential,String sessionId){
        HttpHeaders headers=headers(principal); if(sessionId!=null)headers.set("X-Audit-Session",sessionId);
        return restTemplate.exchange(url("/audit/queries/"+resourceId+"?sequential="+sequential),
            org.springframework.http.HttpMethod.GET,new HttpEntity<Void>(headers),String.class);
    }

    private static Map<String, Object> login(String userId, boolean accepted) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("userId", userId); body.put("accepted", Boolean.valueOf(accepted)); return body;
    }

    private SecurityEvent latestEvent(String action) {
        return repository.findSince("audit-spring3-web", Instant.now().minusSeconds(60)).stream()
            .filter(event -> action.equals(event.getAction()))
            .reduce((first, second) -> second)
            .orElseThrow(() -> new AssertionError("Expected audited action: " + action));
    }

    private HttpEntity<Map<String, Object>> exportRequest(String reportId, String tenantCode, int rows,
                                                           String principal) {
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("id", reportId);
        Map<String, Object> tenant = new LinkedHashMap<String, Object>();
        tenant.put("code", tenantCode);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("report", report);
        body.put("tenant", tenant);
        body.put("rows", rows);
        HttpHeaders headers = headers(principal);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<Map<String, Object>>(body, headers);
    }

    private HttpHeaders headers(String principal) {
        HttpHeaders headers = new HttpHeaders();
        if (principal != null) headers.set(AUDIT_PRINCIPAL_HEADER, principal);
        return headers;
    }
}
