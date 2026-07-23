package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.core.application.MonitoringOutcome;
import io.github.jasper.monitoring.core.application.SecurityMonitor;
import io.github.jasper.monitoring.web.FrontendServerContext;
import io.github.jasper.monitoring.web.FrontendSignal;
import io.github.jasper.monitoring.web.FrontendSignalMapper;

/**
 * 通过标准 Web 契约记录浏览器遥测信号的宿主桥接器。
 *
 * <p>客户端数据只作为补充证据；身份、来源地址、请求标识和时间等可信上下文仍必须由服务端提供。
 * 不应将此类前端信号直接作为授权、封禁或身份判定的唯一依据。</p>
 */
public final class FrontendSignalRecorder {
    private final SecurityMonitor monitor;

    /**
     * @param monitor 用于持久化并评估映射后服务端事件的监测入口
     */
    public FrontendSignalRecorder(SecurityMonitor monitor) {
        this.monitor = monitor;
    }

    /**
     * 校验浏览器遥测数据，将其映射为服务端事件后再记录。
     *
     * @param signal 受前端契约限制的客户端上报数据
     * @param serverContext 服务端解析的请求、身份和时间数据
     * @return 由标准事件草稿产生的监测结果
     */
    public MonitoringOutcome record(FrontendSignal signal, FrontendServerContext serverContext) {
        return monitor.record(FrontendSignalMapper.toDraft(signal, serverContext));
    }
}
