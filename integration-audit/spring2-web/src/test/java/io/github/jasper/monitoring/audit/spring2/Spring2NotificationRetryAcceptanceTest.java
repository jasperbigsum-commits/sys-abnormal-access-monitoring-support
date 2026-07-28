package io.github.jasper.monitoring.audit.spring2;

import io.github.jasper.monitoring.audit.spring2.notification.AuditNotificationChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.jasper.monitoring.core.application.notification.NotificationDeliveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Boot 2 的告警通知持久重试验收测试。
 *
 * <p>模拟通知渠道前两次失败，验证告警先持久化、重试不创建重复告警，以及有限重试最终送达。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:audit-spring2-notification;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "abnormal.access.monitor.notification.channel=tc14",
    "abnormal.access.monitor.notification.retry-enabled=false",
    "abnormal.access.monitor.notification.retry-delay=1ms"
})
class Spring2NotificationRetryAcceptanceTest {
    private static final String PRINCIPAL_HEADER = "X-Audit-Principal";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NotificationDeliveryService deliveries;

    @Test
    @DisplayName("TC-14 notification retries preserve one authoritative alert")
    void tc14_retriesCommittedAlertNotificationUntilThirdAttempt() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set(PRINCIPAL_HEADER, "audit-exporter");

        for (int attempt = 0; attempt < 5; attempt++) {
            ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/audit/login-failure",
                new HttpEntity<Void>(headers), String.class);
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        String alertId = onlyAlertId();
        assertDelivery(alertId, "RETRY_PENDING", 1);

        awaitRetryDelay();
        deliveries.retryDue(10);
        assertEquals(1, alertCount());
        assertDelivery(alertId, "RETRY_PENDING", 2);

        awaitRetryDelay();
        deliveries.retryDue(10);
        assertEquals(1, alertCount());
        assertDelivery(alertId, "DELIVERED", 3);
    }

    private String onlyAlertId() {
        assertEquals(1, alertCount());
        return jdbc.queryForObject("SELECT alert_id FROM security_alert WHERE rule_id = 'AUTH-01'", String.class);
    }

    private int alertCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM security_alert WHERE rule_id = 'AUTH-01'", Integer.class);
    }

    private void assertDelivery(String alertId, String status, int attempts) {
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM notification_delivery WHERE channel = 'tc14' AND aggregate_id = ?",
            Integer.class, alertId));
        assertEquals(status, jdbc.queryForObject(
            "SELECT status FROM notification_delivery WHERE channel = 'tc14' AND aggregate_id = ?",
            String.class, alertId));
        assertEquals(attempts, jdbc.queryForObject(
            "SELECT attempt_count FROM notification_delivery WHERE channel = 'tc14' AND aggregate_id = ?",
            Integer.class, alertId));
    }

    private static void awaitRetryDelay() throws InterruptedException {
        Thread.sleep(5L);
    }
}
