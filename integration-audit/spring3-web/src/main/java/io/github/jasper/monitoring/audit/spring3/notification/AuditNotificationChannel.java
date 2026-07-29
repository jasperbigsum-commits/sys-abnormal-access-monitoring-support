package io.github.jasper.monitoring.audit.spring3.notification;

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
 * <p>每个 deliveryId 的前两次调用故意失败，第三次成功。调用前先检查告警行已经提交，
 * 用来验证通知失败不会回滚告警事务；attempts 只属于测试进程内的故障注入，不代表真实供应商
 * 的投递状态。</p>
 */
@Component
public final class AuditNotificationChannel implements NotificationChannel {
    private final JdbcTemplate jdbc;
    private final ConcurrentMap<String, AtomicInteger> attempts =
        new ConcurrentHashMap<String, AtomicInteger>();

    public AuditNotificationChannel(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 模拟下游通知调用，并按稳定 deliveryId 统计故障注入次数。
     *
     * @param deliveryId 组件生成的投递幂等键
     * @param alert 已经持久化的告警
     */
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
