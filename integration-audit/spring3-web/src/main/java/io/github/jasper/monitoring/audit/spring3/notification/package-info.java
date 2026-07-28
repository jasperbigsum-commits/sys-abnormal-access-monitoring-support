/**
 * Boot 3 宿主的通知投递夹具。
 *
 * <p>通过可控失败模拟持久化通知重试，验证告警提交与外部投递解耦。生产实现应接入真实渠道，
 * 传递投递幂等键，并且不得因通知失败回滚业务事务或已持久化告警。</p>
 */
package io.github.jasper.monitoring.audit.spring3.notification;
