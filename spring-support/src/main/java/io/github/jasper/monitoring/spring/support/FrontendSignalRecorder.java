package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import io.github.jasper.monitoring.web.FrontendServerContext;
import io.github.jasper.monitoring.web.FrontendSignal;
import java.util.Objects;

/**
 * 通过标准 Web 契约记录浏览器遥测信号的宿主桥接器。
 *
 * <p>客户端数据只作为补充证据；身份、来源地址、请求标识和时间等可信上下文仍必须由服务端提供。
 * 不应将此类前端信号直接作为授权、封禁或身份判定的唯一依据。</p>
 */
public final class FrontendSignalRecorder {
    private final MonitoringService monitor;

    /**
     * @param monitor 用于持久化并评估映射后服务端事件的监测入口
     */
    public FrontendSignalRecorder(MonitoringService monitor) {
        this.monitor = Objects.requireNonNull(monitor, "monitor");
    }

    /**
     * 校验浏览器遥测数据，将其映射为服务端事件后再记录。
     *
     * @param signal 受前端契约限制的客户端上报数据
     * @param serverContext 服务端解析的请求、身份和时间数据
     * @return 由标准事件草稿产生的监测结果
     */
    public SecurityEventAssembler.AssemblyResult record(FrontendSignal signal, FrontendServerContext serverContext) {
        MonitoringRequestContext request = MonitoringRequestContext.builder().method("CLIENT")
            .path(signal.getRoute()).sourceIp(serverContext.getSourceIp()).requestId(signal.getRequestId())
            .traceId(signal.getTraceId()).build();
        IdentityContext identity = new IdentityContext(serverContext.getUserId(), serverContext.getAccountType(),
            serverContext.getRoleIds(), serverContext.getSessionIdHash());
        ActionFacts.Builder facts = ActionFacts.builder();
        if (signal.getResourceIdHash() != null) {
            facts.put(BuiltInFacts.ResourceId.class, signal.getResourceIdHash());
        } else {
            facts.put(BuiltInFacts.ResourceId.class, signal.getRoute());
        }
        return monitor.monitor(ActionExecution.of(BuiltInActions.FrontendSignal.class,
            request, identity, ActionOutcome.success(0L), facts.build(), FactSource.CLIENT_SUPPLEMENTAL));
    }
}
