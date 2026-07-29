package io.github.jasper.monitoring.audit.spring3.notification;

import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.port.NotificationChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Deterministic host channel used to exercise durable notification retries. */
@Component
public final class AuditNotificationChannel implements NotificationChannel {
    private final JdbcTemplate jdbc;
    private final ConcurrentMap<String, AtomicInteger> attempts =
        new ConcurrentHashMap<String, AtomicInteger>();

    public AuditNotificationChannel(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void notify(String deliveryId, SecurityAlert alert) {
        Integer alertRows = jdbc.queryForObject(
            "SELECT COUNT(*) FROM monitoring_security_alert WHERE alert_id = ?", Integer.class, alert.getAlertId());
        if (alertRows == null || alertRows.intValue() != 1) {
            throw new AssertionError("notification must run after exactly one alert row commits");
        }
        int attempt = attempts.computeIfAbsent(deliveryId, key -> new AtomicInteger()).incrementAndGet();
        if (attempt <= 2) {
            throw new IllegalStateException("simulated notification provider failure");
        }
    }
}
