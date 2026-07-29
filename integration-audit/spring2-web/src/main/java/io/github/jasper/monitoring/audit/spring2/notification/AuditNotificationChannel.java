package io.github.jasper.monitoring.audit.spring2.notification;

import io.github.jasper.monitoring.core.domain.SecurityAlert;
import io.github.jasper.monitoring.core.port.NotificationChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 用于验证持久通知重试的确定性宿主渠道。
 *
 * <p>该实现按投递 ID 记录调用次数，并在预定次数内故意失败，验证告警已经提交后通知失败不会
 * 回滚告警，后续重试也不会制造重复告警。它是夹具故障注入实现，不是生产通知渠道。</p>
 */
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
