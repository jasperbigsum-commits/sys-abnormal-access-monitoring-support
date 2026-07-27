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
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import io.github.jasper.monitoring.audit.spring3.report.ReportExportService;
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

    @Autowired
    private ReportExportService policyExports;

    @BeforeEach
    void resetExportSideEffects() {
        exportService.reset();
    }

    @Test
    @DisplayName("IA-01 production audit evidence uses MyBatis persistence")
    void ia01_productionAuditEvidenceUsesMyBatisPersistence() {
        assertTrue(repository instanceof MyBatisMonitoringStore,
            "The integration host must persist audit evidence through MyBatis, never the memory adapter");
        assertEquals("ACTIVE", String.valueOf(fixtures.findAccount("audit-exporter").get("STATUS")));
        assertEquals("report-a", String.valueOf(fixtures.findReport("report-a").get("REPORTID")));
        assertTrue(fixtures.findRoles("audit-admin").contains("audit-admin"));
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM security_event", Long.class).longValue();
        assertEquals(HttpStatus.OK, post("/audit/login-failure", "audit-viewer").getStatusCode());
        assertEquals(before + 1L,
            jdbc.queryForObject("SELECT COUNT(*) FROM security_event", Long.class).longValue());
    }

    @Test
    @DisplayName("IA-02 request context alone never creates a business event")
    void ia02_requestContextAloneNeverCreatesBusinessEvent() {
        long before = repository.findSince("audit-spring3-web", Instant.EPOCH).size();
        ResponseEntity<String> response = get("/audit/context-only", "audit-viewer");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("requestId"));
        assertEquals(before, repository.findSince("audit-spring3-web", Instant.EPOCH).size());
    }

    @Test
    @DisplayName("IA-07 report interceptor owns the fail-closed authorization boundary")
    void ia07_reportInterceptorOwnsFailClosedAuthorizationBoundary() {
        assertEquals(HttpStatus.OK, get("/audit/reports/report-a", "audit-exporter").getStatusCode());
        assertEquals(HttpStatus.OK,
            post("/audit/reports/report-a/export", "audit-exporter").getStatusCode());
        assertEquals(1, exportService.getInvocationCount());
    }

    @Test
    @DisplayName("TC-05 cross-organization access stops before resource disclosure")
    void tc05_crossOrganizationAccessStopsBeforeResourceDisclosure() {
        ResponseEntity<String> response = post("/audit/reports/report-b/export", "audit-exporter");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(0, exportService.getInvocationCount());
        assertTrue(repository.findSince("audit-spring3-web", Instant.EPOCH).stream()
            .anyMatch(event -> "authz:access-denied".equals(event.getAction())
                && "report-b".equals(event.getResourceId())));
    }

    @Test
    @DisplayName("TC-04 regular users cannot invoke management operations")
    void tc04_regularUsersCannotInvokeManagementOperations() {
        ResponseEntity<String> response = get("/audit/management/events", "audit-viewer");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(0, exportService.getInvocationCount());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM management_audit WHERE actor_id = ? "
            + "AND action = ? AND outcome = ?", Long.class,
            "audit-viewer", "EVENT_READ", "DENIED").longValue() >= 1L);
    }

    @Test
    @DisplayName("IA-05 client input cannot override server-computed export facts")
    void ia05_clientInputCannotOverrideServerComputedExportFacts() {
        ResponseEntity<String> response = restTemplate.postForEntity(url("/audit/export"),
            exportRequest("client-forged-report", "client-forged-org", 9000, "audit-exporter"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        SecurityEvent event = latestEvent("report:export");
        assertEquals(37L, event.getDataCount());
        assertEquals("audit-export-2026", event.getResourceId());
        assertTrue(!repository.findOpen("EXPT-01|audit-exporter|report:audit-export-2026").isPresent());
    }

    @Test
    @DisplayName("IA-03 annotated actions classify success and denied outcomes")
    void ia03_annotatedActionsClassifySuccessAndDeniedOutcomes() {
        ResponseEntity<String> response = get("/audit/annotated-query", "audit-exporter");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(SecurityEventType.QUERY, latestEvent("data:query").getEventType());
        ResponseEntity<String> denied = restTemplate.postForEntity(url("/audit/annotated-export-denied"),
            exportRequest("denied-report", "org-denied", 12, "audit-exporter"), String.class);
        assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
        assertEquals(SecurityEventResult.DENIED, latestEvent("data:query").getResult());
    }

    @Test
    @DisplayName("IA-04 nested action facts are typed and prevalidated")
    void ia04_nestedActionFactsAreTypedAndPrevalidated() {
        ResponseEntity<String> response = restTemplate.postForEntity(url("/audit/annotated-export"),
            exportRequest("audit-export-2026", "org-a", 5000, "audit-exporter"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        SecurityEvent event = latestEvent("resource:view-sensitive");
        assertEquals(SecurityEventType.VIEW_SENSITIVE, event.getEventType());
        assertEquals(SecurityEventResult.SUCCESS, event.getResult());
        assertEquals(5000L, event.getDataCount());
        assertEquals("METHOD_PARAMETER", jdbc.queryForObject("SELECT source_type FROM security_event_fact "
            + "WHERE event_id=? AND fact_key='data_count'", String.class, event.getEventId()));
    }

    @Test
    @DisplayName("IA-11 management identity is server-derived and reauthorized")
    void ia11_managementIdentityIsServerDerivedAndReauthorized() {
        restTemplate.postForEntity(url("/audit/export"),
            exportRequest("ignored", "ignored", 1, "audit-exporter"), String.class);
        ResponseEntity<String> response = get("/audit/management/events", "audit-admin");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("\"count\":"));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM management_audit WHERE system_id = ? "
            + "AND actor_id = ? AND action = ? AND outcome = ?", Long.class,
            "audit-spring3-web", "audit-admin", "EVENT_READ", "SUCCEEDED").longValue() >= 1L);
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
        assertEquals(HttpStatus.FORBIDDEN,
            postJson("/audit/management/sessions/tc11-user/revoke", "audit-viewer", request).getStatusCode());
        assertEquals(2L, fixtures.countActiveSessions("tc11-user"));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM audit_control_state "
            + "WHERE idempotency_key='tc11-revoke-once'", Long.class).longValue());
        assertEquals(HttpStatus.OK,
            postJson("/audit/management/sessions/tc11-user/revoke", "audit-admin", request).getStatusCode());
        ResponseEntity<String> replay =
            postJson("/audit/management/sessions/tc11-user/revoke", "audit-admin", request);
        assertEquals(HttpStatus.OK, replay.getStatusCode());
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

    @Test
    @DisplayName("TC-08 high-risk export is stopped before XLSX generation")
    void tc08_highRiskExportIsStoppedBeforeXlsxGeneration() throws Exception {
        ResponseEntity<byte[]> allowed=export("audit-exporter",exportBody(1L,4999L,
            java.util.Arrays.asList("rowId","displayValue","amount")));
        assertEquals(HttpStatus.OK,allowed.getStatusCode());
        try(XSSFWorkbook workbook=new XSSFWorkbook(new ByteArrayInputStream(allowed.getBody()))){
            assertEquals(5000,workbook.getSheetAt(0).getPhysicalNumberOfRows());
            assertEquals(3,workbook.getSheetAt(0).getRow(0).getPhysicalNumberOfCells());
            assertEquals("amount",workbook.getSheetAt(0).getRow(0).getCell(2).getStringCellValue());
        }
        int generated=policyExports.getWorkbookInvocationCount();
        ResponseEntity<byte[]> blocked=export("audit-exporter",exportBody(1L,5000L,
            java.util.Arrays.asList("rowId","displayValue","amount")));
        assertEquals(HttpStatus.ACCEPTED,blocked.getStatusCode());
        assertEquals(generated,policyExports.getWorkbookInvocationCount());
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM security_alert "
            + "WHERE rule_id='EXPT-01' AND subject='audit-exporter'",Long.class).longValue());

        jdbc.update("INSERT INTO audit_account(user_id,organization_id,status) VALUES(?,?,?)",
            "audit-export-sensitive", "org-a", "ACTIVE");
        jdbc.update("INSERT INTO audit_user_role(user_id,role_id,granted_by,granted_at) VALUES(?,?,?,?)",
            "audit-export-sensitive", "audit-exporter", "fixture", java.sql.Timestamp.from(Instant.EPOCH));
        int beforeSensitive = policyExports.getWorkbookInvocationCount();
        assertEquals(HttpStatus.ACCEPTED, export("audit-export-sensitive", exportBody(1L, 1L,
            java.util.Arrays.asList("rowId", "sensitiveValue"))).getStatusCode());
        assertEquals(beforeSensitive, policyExports.getWorkbookInvocationCount());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM security_alert "
            + "WHERE rule_id='EXPT-01' AND subject='audit-export-sensitive'", Long.class).longValue());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM control_action c JOIN security_alert a "
            + "ON a.alert_id=c.alert_id WHERE a.subject='audit-export-sensitive' "
            + "AND c.action_type='REQUIRE_APPROVAL'", Long.class).longValue());
    }

    @Test
    @DisplayName("TC-09 daily aggregate blocks the threshold-crossing export")
    void tc09_dailyAggregateBlocksTheThresholdCrossingExport() {
        Map<String,Object> fourThousand=exportBody(1L,4000L,java.util.Arrays.asList("rowId","displayValue"));
        assertEquals(HttpStatus.OK,export("audit-export-daily",fourThousand).getStatusCode());
        assertEquals(HttpStatus.OK,export("audit-export-daily",fourThousand).getStatusCode());
        int generated=policyExports.getWorkbookInvocationCount();
        assertEquals(HttpStatus.ACCEPTED,export("audit-export-daily",exportBody(1L,2000L,
            java.util.Arrays.asList("rowId","displayValue"))).getStatusCode());
        assertEquals(generated,policyExports.getWorkbookInvocationCount());
        assertEquals(8000L,jdbc.queryForObject("SELECT SUM(row_count) FROM audit_export_ledger "
            + "WHERE user_id='audit-export-daily' AND outcome='SUCCEEDED'",Long.class).longValue());
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM security_alert "
            + "WHERE rule_id='EXPT-02' AND subject='audit-export-daily'",Long.class).longValue());
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

    private ResponseEntity<byte[]> export(String principal,Map<String,Object> body){
        HttpHeaders headers=headers(principal);headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url("/audit/reports/report-a/exports"),org.springframework.http.HttpMethod.POST,
            new HttpEntity<Map<String,Object>>(body,headers),byte[].class);
    }

    private static Map<String,Object> exportBody(long minId,long maxId,List<String> fields){
        Map<String,Object> body=new LinkedHashMap<String,Object>();
        body.put("minId",Long.valueOf(minId));body.put("maxId",Long.valueOf(maxId));body.put("fields",fields);return body;
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
        report.put("rows", Integer.valueOf(rows));
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
