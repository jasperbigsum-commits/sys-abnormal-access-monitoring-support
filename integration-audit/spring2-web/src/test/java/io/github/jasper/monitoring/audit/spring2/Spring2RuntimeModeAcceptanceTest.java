package io.github.jasper.monitoring.audit.spring2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import io.github.jasper.monitoring.spring2.autoconfigure.AbnormalAccessMonitorProperties;

class Spring2RuntimeModeAcceptanceTest {
    @Test
    @DisplayName("TC-17 observe records without blocking while enforce applies controls")
    void tc17_observeRecordsWithoutBlockingWhileEnforceAppliesControls() {
        try (ConfigurableApplicationContext observe = start("OBSERVE", "audit-spring2-tc17-observe")) {
            assertEquals(io.github.jasper.monitoring.api.MonitoringMode.OBSERVE,
                observe.getBean(AbnormalAccessMonitorProperties.class).getMode());
            TestRestTemplate http = new TestRestTemplate();
            String base = baseUrl(observe);
            assertEquals(HttpStatus.UNAUTHORIZED,
                http.getForEntity(base + "/audit/reports/report-a", String.class).getStatusCode());
            for (int attempt = 0; attempt < 6; attempt++) {
                assertEquals(HttpStatus.FORBIDDEN, login(http, base, "tc01-user").getStatusCode());
            }
            JdbcTemplate jdbc = observe.getBean(JdbcTemplate.class);
            assertEquals(1L, count(jdbc, "SELECT COUNT(*) FROM security_alert WHERE rule_id='AUTH-01'"));
            assertEquals(0L, count(jdbc, "SELECT COUNT(*) FROM audit_control_state"));
        }

        try (ConfigurableApplicationContext enforce = start("ENFORCE", "audit-spring2-tc17-enforce")) {
            assertEquals(io.github.jasper.monitoring.api.MonitoringMode.ENFORCE,
                enforce.getBean(AbnormalAccessMonitorProperties.class).getMode());
            TestRestTemplate http = new TestRestTemplate();
            String base = baseUrl(enforce);
            for (int attempt = 0; attempt < 5; attempt++) {
                assertEquals(HttpStatus.FORBIDDEN, login(http, base, "tc01-user").getStatusCode());
            }
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, login(http, base, "tc01-user").getStatusCode());
            assertEquals(2L, count(enforce.getBean(JdbcTemplate.class),
                "SELECT COUNT(*) FROM audit_control_state WHERE subject='tc01-user'"));
        }
    }

    private static ConfigurableApplicationContext start(String mode, String database) {
        return new SpringApplicationBuilder(Spring2AuditApplication.class)
            .web(WebApplicationType.SERVLET)
            .properties("spring.main.banner-mode=off", "spring.jmx.enabled=false")
            .run("--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:" + database + ";MODE=MySQL;DB_CLOSE_DELAY=0",
                "--abnormal.access.monitor.mode=" + mode);
    }

    private static ResponseEntity<String> login(TestRestTemplate http, String base, String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("userId", userId);
        body.put("accepted", Boolean.FALSE);
        return http.exchange(base + "/audit/authentication/login", HttpMethod.POST,
            new HttpEntity<Map<String, Object>>(body, headers), String.class);
    }

    private static String baseUrl(ConfigurableApplicationContext context) {
        return "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Long.class).longValue();
    }
}
