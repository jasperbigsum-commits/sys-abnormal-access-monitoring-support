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
     * <p>实现仅应针对投递失败抛出异常；监测器无论通知是否成功都会保留告警记录。
     * 租约过期恢复可能使用同一 {@code deliveryId} 再次调用，因此实现必须按该标识幂等。</p>
     *
     * @param deliveryId 稳定的投递幂等键；实现应将其传递给下游提供方
     * @param alert 待通知的告警摘要
     */
    void notify(String deliveryId, SecurityAlert alert);

    /** @return 有意抑制外部投递、但保留完整监测行为的空通知通道 */
    static NotificationChannel noop() {
        return new NotificationChannel() {
            @Override
            public void notify(String deliveryId, SecurityAlert alert) {
                // 通知投递是尽力而为行为，不应影响宿主业务事务提交。
            }
        };
    }
}
