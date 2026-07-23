package io.github.jasper.monitoring.core.application;

import io.github.jasper.monitoring.core.port.ControlHandler;



import io.github.jasper.monitoring.api.SecurityEventDraft;

/**
 * 宿主应用显式采集安全领域事件的主入口（Security Monitor）。
 *
 * <p>调用方只能传入已经由服务端确认的身份、请求和业务结果；前端上报内容只能作为补充属性，
 * 不能作为用户身份、来源地址或授权结论的依据。</p>
 */
public interface SecurityMonitor {
    /**
     * 持久化并评估一条已校验的服务端事件草稿。
     *
     * <p>在观察模式（{@code OBSERVE}）下不会执行控制动作；在执行模式（{@code ENFORCE}）下，
     * 可能调用已配置的宿主 {@link ControlHandler}。调用此底层入口时，持久化或规则计算异常会直接
     * 返回给调用方；Web 拦截器等边界适配层可按宿主策略将监测异常隔离开。</p>
     *
     * @param draft 已校验的事件数据；不得包含原始密钥，也不得使用客户端声称的身份字段
     * @return 已持久化事件及其产生的规则命中、告警和控制执行结果
     * @throws RuntimeException 当事件转换、持久化或规则评估不能完成时
     */
    MonitoringOutcome record(SecurityEventDraft draft);
}
