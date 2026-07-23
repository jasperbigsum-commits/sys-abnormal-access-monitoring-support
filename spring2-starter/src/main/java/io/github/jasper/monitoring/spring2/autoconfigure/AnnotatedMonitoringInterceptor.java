package io.github.jasper.monitoring.spring2.autoconfigure;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.IdentityContextProvider;
import io.github.jasper.monitoring.api.MonitorAction;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.TrustedProxyResolver;
import io.github.jasper.monitoring.core.application.ActionEventRecorder;
import io.github.jasper.monitoring.spring.support.MdcTraceBridge;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 记录带 {@link MonitorAction} 的已完成 MVC 动作，不改变宿主控制器的执行路径。
 *
 * <p>方法注解覆盖类型注解；仅 {@code HandlerMethod} 会被自动处理。该拦截器在请求完成后根据服务端状态
 * 写入事件，监测失败不会改变已完成响应。它不等价于 Service AOP，非 MVC 入口应使用注册式方法调用埋点。</p>
 */
public final class AnnotatedMonitoringInterceptor implements HandlerInterceptor {
    private static final String ACTION_ATTRIBUTE = "io.github.jasper.monitoring.monitor-action";
    private final ActionEventRecorder recorder;
    private final TrustedProxyResolver trustedProxyResolver;
    private final IdentityContextProvider identityContextProvider;
    private final MdcTraceBridge mdcTraceBridge;

    /**
     * @param recorder 注解与方法调用共用的标准记录器
     * @param trustedProxyResolver 解析服务端观察到的客户端地址
     * @param identityContextProvider 宿主权威身份解析器
     */
    public AnnotatedMonitoringInterceptor(ActionEventRecorder recorder, TrustedProxyResolver trustedProxyResolver,
                                          IdentityContextProvider identityContextProvider) {
        this(recorder, trustedProxyResolver, identityContextProvider, MdcTraceBridge.create(false, "traceId"));
    }

    /**
     * @param recorder 注解与方法调用共用的标准记录器
     * @param trustedProxyResolver 解析服务端观察到的客户端地址
     * @param identityContextProvider 宿主权威身份解析器
     * @param mdcTraceBridge 可选日志 MDC 链路追踪桥接器
     */
    public AnnotatedMonitoringInterceptor(ActionEventRecorder recorder, TrustedProxyResolver trustedProxyResolver,
                                          IdentityContextProvider identityContextProvider,
                                          MdcTraceBridge mdcTraceBridge) {
        this.recorder = recorder;
        this.trustedProxyResolver = trustedProxyResolver;
        this.identityContextProvider = identityContextProvider;
        this.mdcTraceBridge = mdcTraceBridge;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        MonitorAction action = action(handler);
        if (action == null) {
            return true;
        }
        try {
            RequestMetadataInterceptor.populate(request, trustedProxyResolver, identityContextProvider,
                mdcTraceBridge, "annotated-action");
            request.setAttribute(ACTION_ATTRIBUTE, action);
        } catch (RuntimeException ignored) {
            // Monitoring must not affect the host request when trusted context resolution fails.
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        try {
            Object action = request.getAttribute(ACTION_ATTRIBUTE);
            Object requestContext = request.getAttribute(RequestMetadataInterceptor.REQUEST_CONTEXT_ATTRIBUTE);
            Object identity = request.getAttribute(RequestMetadataInterceptor.IDENTITY_CONTEXT_ATTRIBUTE);
            if (!(action instanceof MonitorAction) || !(requestContext instanceof MonitoringRequestContext)
                || !(identity instanceof IdentityContext)) {
                return;
            }
            recorder.record((MonitorAction) action, (MonitoringRequestContext) requestContext,
                (IdentityContext) identity, result(response, exception), reasonCode(response, exception));
        } catch (RuntimeException ignored) {
            // Observational monitoring cannot fail the completed host action.
        } finally {
            RequestMetadataInterceptor.clearMdcScope(request, "annotated-action");
        }
    }

    private static MonitorAction action(Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return null;
        }
        HandlerMethod method = (HandlerMethod) handler;
        MonitorAction action = method.getMethodAnnotation(MonitorAction.class);
        return action == null ? AnnotationUtils.findAnnotation(method.getBeanType(), MonitorAction.class) : action;
    }

    private static SecurityEventResult result(HttpServletResponse response, Exception exception) {
        int status = response.getStatus();
        if (status == HttpServletResponse.SC_UNAUTHORIZED || status == HttpServletResponse.SC_FORBIDDEN) {
            return SecurityEventResult.DENIED;
        }
        return exception != null || status >= HttpServletResponse.SC_BAD_REQUEST
            ? SecurityEventResult.FAILURE : SecurityEventResult.SUCCESS;
    }

    private static String reasonCode(HttpServletResponse response, Exception exception) {
        int status = response.getStatus();
        if (status == HttpServletResponse.SC_UNAUTHORIZED || status == HttpServletResponse.SC_FORBIDDEN) {
            return "HTTP_" + status;
        }
        if (exception != null) {
            return "HANDLER_EXCEPTION";
        }
        return status >= HttpServletResponse.SC_BAD_REQUEST ? "HTTP_" + status : null;
    }
}
