package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.SecurityAlert;


/**
 * 为新建或刷新告警发送外部通知的尽力而为端口。
 *
 * <p>通知失败不会回滚已保存的告警，也不应改变宿主业务事务。</p>
 */
public interface NotificationChannel {
    /**
     * 发送一条告警通知。
     *
     * <p>实现仅应针对投递失败抛出异常；监测器无论通知是否成功都会保留告警记录。</p>
     *
     * @param alert 待通知的告警摘要
     */
    void notify(SecurityAlert alert);

    /** @return 有意抑制外部投递、但保留完整监测行为的空通知通道 */
    static NotificationChannel noop() {
        return new NotificationChannel() {
            @Override
            public void notify(SecurityAlert alert) {
                // Notifications are intentionally best-effort and never control a business transaction.
            }
        };
    }
}
