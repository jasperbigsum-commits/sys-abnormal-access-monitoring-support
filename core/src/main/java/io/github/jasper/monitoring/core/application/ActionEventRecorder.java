package io.github.jasper.monitoring.core.application;





import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitorAction;
import io.github.jasper.monitoring.api.MonitorActionDefinition;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 将统一动作定义、可信上下文和运行时业务事实转换为标准监测事件。
 *
 * <p>MVC 拦截器把 {@link MonitorAction} 转换为 {@link MonitorActionDefinition} 后调用本类；服务、消息
 * 消费和任务则使用 {@link MonitoringActionRegistry} 中的动作编码调用 {@link #record(String,
 * MonitoringRequestContext, IdentityContext, SecurityEventResult, String)}。两种入口最终使用完全相同的
 * 静态元数据和可信 IP、身份、会话、请求追踪信息。</p>
 *
 * <p>本类不依赖 Servlet 或 Spring。它只预填充可信字段；调用 {@link #draft(String,
 * MonitoringRequestContext, IdentityContext)} 后可补充动态资源 ID、数量、时延和已批准属性，随后交给
 * {@link SecurityMonitor} 记录。</p>
 */
public final class ActionEventRecorder {
    private final SecurityMonitor monitor;
    private final Clock clock;
    private final MonitoringActionRegistry actions;

    /**
     * 创建带空动作注册表的记录器。
     *
     * <p>仅注解埋点可使用此构造器；要使用按动作编码的手工埋点，请传入已注册的
     * {@link MonitoringActionRegistry}。</p>
     *
     * @param monitor 标准事件记录入口
     * @param clock 服务端事件时间来源
     */
    public ActionEventRecorder(SecurityMonitor monitor, Clock clock) {
        this(monitor, clock, new MonitoringActionRegistry());
    }

    /**
     * 创建可同时支持注解与注册式方法调用埋点的记录器。
     *
     * @param monitor 标准事件记录入口
     * @param clock 服务端事件时间来源
     * @param actions 启动期动作定义注册表
     */
    public ActionEventRecorder(SecurityMonitor monitor, Clock clock, MonitoringActionRegistry actions) {
        this.monitor = Objects.requireNonNull(monitor, "monitor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    /**
     * 使用注解静态元数据记录一次已完成或已拒绝的动作。
     *
     * @param action 宿主适配器解析出的动作注解
     * @param request 可信请求快照
     * @param identity 服务端解析的身份；匿名时使用 {@link IdentityContext#anonymous()}
     * @param result 服务端确认的最终动作结果
     * @param reasonCode 可选的稳定、非敏感原因码
     * @return 标准监测器产生的处理结果
     */
    public MonitoringOutcome record(MonitorAction action, MonitoringRequestContext request,
                                    IdentityContext identity, SecurityEventResult result, String reasonCode) {
        return record(MonitorActionDefinition.from(action), request, identity, result, reasonCode);
    }

    /**
     * 按启动期注册的动作编码记录一次动作，适用于 Service、消息消费和定时任务。
     *
     * @param action 已注册的稳定动作编码
     * @param request 可信请求或调用上下文
     * @param identity 服务端解析的身份
     * @param result 服务端确认的最终动作结果
     * @param reasonCode 可选的稳定、非敏感原因码
     * @return 标准监测器产生的处理结果
     */
    public MonitoringOutcome record(String action, MonitoringRequestContext request, IdentityContext identity,
                                    SecurityEventResult result, String reasonCode) {
        return record(actions.require(action), request, identity, result, reasonCode);
    }

    /**
     * 从一个已解析的静态动作定义记录事件。
     *
     * @param action 注解或注册表解析出的动作定义
     * @param request 可信请求或调用上下文
     * @param identity 服务端解析的身份
     * @param result 服务端确认的最终动作结果
     * @param reasonCode 可选的稳定、非敏感原因码
     * @return 标准监测器产生的处理结果
     */
    public MonitoringOutcome record(MonitorActionDefinition action, MonitoringRequestContext request,
                                    IdentityContext identity, SecurityEventResult result, String reasonCode) {
        return monitor.record(draft(action, request, identity).result(result).reasonCode(reasonCode).build());
    }

    /**
     * 记录由 {@link #draft(String, MonitoringRequestContext, IdentityContext)} 扩展完成的动态草稿。
     *
     * @param draft 已补充结果和动态业务事实、并完成校验的安全事件草稿
     * @return 标准监测器产生的处理结果
     */
    public MonitoringOutcome record(SecurityEventDraft draft) {
        return monitor.record(Objects.requireNonNull(draft, "draft"));
    }

    /**
     * 以注册动作定义和可信上下文预填充可扩展草稿。
     *
     * <p>调用方只应补充动态、已脱敏的业务事实，例如资源 ID、数据量、时延和非敏感属性；不要覆盖
     * 事件类别、动作编码、来源 IP、请求 ID、身份或服务端时间。</p>
     *
     * @param action 已注册的稳定动作编码
     * @param request 可信请求或调用上下文
     * @param identity 服务端解析的身份
     * @return 已预填充可信静态字段的草稿构建器
     */
    public SecurityEventDraft.Builder draft(String action, MonitoringRequestContext request,
                                            IdentityContext identity) {
        return draft(actions.require(action), request, identity);
    }

    /**
     * 以一个已解析的静态动作定义和可信上下文预填充可扩展草稿。
     *
     * @param action 注解或注册表解析出的动作定义
     * @param request 可信请求或调用上下文
     * @param identity 服务端解析的身份
     * @return 已预填充可信静态字段的草稿构建器
     */
    public SecurityEventDraft.Builder draft(MonitorActionDefinition action, MonitoringRequestContext request,
                                            IdentityContext identity) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(identity, "identity");
        SecurityEventDraft.Builder draft = SecurityEventDraft.builder()
            .eventType(action.getEventType())
            .action(action.getAction())
            .sourceIp(request.getSourceIp())
            .requestId(request.getRequestId())
            .traceId(request.getTraceId())
            .userId(identity.getUserId())
            .accountType(identity.getAccountType())
            .roleIds(identity.getRoleIds())
            .sessionIdHash(identity.getSessionIdHash())
            .occurredAt(Instant.now(clock));
        if (action.getResourceType() != null) {
            draft.resourceType(action.getResourceType());
        }
        for (Map.Entry<String, String> attribute : action.getAttributes().entrySet()) {
            draft.attribute(attribute.getKey(), attribute.getValue());
        }
        for (String ruleTag : action.getRuleTags()) {
            draft.attribute(MonitorActionDefinition.ruleTagAttributeKey(ruleTag), "true");
        }
        return draft;
    }
}
