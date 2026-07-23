package io.github.jasper.monitoring.audit.spring3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.core.application.control.AnnotatedControlHandler;
import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.port.ControlHandler;
import io.github.jasper.monitoring.core.port.MonitoringRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Spring3AuditWebAcceptanceTest {
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MonitoringRepository repository;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void auditsFiveLoginFailuresOverHttpWithAlertAndControlEvidence() {
        assertTrue(applicationContext.containsBean("auditControlActions"),
            "The sample host must expose its @ControlTrigger methods as a normal Spring bean");
        Object controlActions = applicationContext.getBean("auditControlActions");
        assertTrue(AnnotatedControlHandler.hasBindings(controlActions));
        assertTrue(applicationContext.getBeansOfType(ControlHandler.class).isEmpty(),
            "The sample host must use @ControlTrigger instead of a handwritten ControlHandler bean");

        ResponseEntity<String> lastResponse = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            lastResponse = restTemplate.postForEntity(url("/audit/login-failure"), null, String.class);
            assertEquals(HttpStatus.OK, lastResponse.getStatusCode());
        }

        assertTrue(lastResponse.getBody().contains("\"matchCount\":1"),
            "The fifth login failure must report the AUTH-01 match through the real HTTP response");
        List<SecurityEvent> events = repository.findEventsSince(Instant.now().minusSeconds(60));
        assertEquals(5, events.stream().filter(event -> "audit:login-failure".equals(event.getAction())).count());
        assertTrue(events.stream().filter(event -> "audit:login-failure".equals(event.getAction()))
            .allMatch(event -> "audit-user".equals(event.getUserId())));
        SecurityAlert alert = repository.findOpenAlert("AUTH-01|audit-user|account:").get();
        assertTrue(repository.findControl(alert.getAlertId() + ":" + ControlActionType.REQUIRE_CAPTCHA).isPresent());
        assertTrue(repository.findControl(alert.getAlertId() + ":" + ControlActionType.RATE_LIMIT).isPresent());
    }

    @Test
    void auditsRegisteredExportWithDynamicFactsOverHttp() {
        ResponseEntity<String> response = restTemplate.postForEntity(url("/audit/export"), null, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        SecurityEvent event = latestEvent("audit:export");
        assertEquals(5000L, event.getDataCount());
        assertEquals("audit-export-2026", event.getResourceId());
        assertEquals("true", event.getAttribute("monitor.rule-tag.sensitive-data"));
        assertTrue(repository.findOpenAlert("EXPT-01|audit-user|report:audit-export-2026").isPresent());
    }

    @Test
    void recordsAnnotatedMvcActionOverHttp() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/audit/annotated-query"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(SecurityEventType.QUERY, latestEvent("audit:annotated-query").getEventType());
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private SecurityEvent latestEvent(String action) {
        return repository.findEventsSince(Instant.now().minusSeconds(60)).stream()
            .filter(event -> action.equals(event.getAction()))
            .reduce((first, second) -> second)
            .orElseThrow(() -> new AssertionError("Expected audited action: " + action));
    }
}
