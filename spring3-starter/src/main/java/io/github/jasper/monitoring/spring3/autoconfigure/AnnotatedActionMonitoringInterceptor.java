package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.IdentityContextProvider;
import io.github.jasper.monitoring.api.MonitorAction;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.TrustedProxyResolver;
import io.github.jasper.monitoring.core.application.ActionEventRecorder;
import io.github.jasper.monitoring.spring.support.MdcTraceBridge;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 记录带 {@link MonitorAction} 的已完成 MVC 动作，不改变宿主控制器的执行路径。
 *
 * <p>方法注解覆盖类型注解；仅 {@code HandlerMethod} 会被自动处理。该拦截器在请求完成后根据服务端状态
 * 写入事件，监测失败不会改变已完成响应。它不等价于 Service AOP，非 MVC 入口应使用注册式方法调用埋点。</p>
 */
public final class AnnotatedActionMonitoringInterceptor implements HandlerInterceptor {
    private final ActionEventRecorder recorder;
    private final TrustedProxyResolver trustedProxyResolver;
    private final IdentityContextProvider identityContextProvider;
    private final MdcTraceBridge mdcTraceBridge;

    /**
     * @param recorder 注解与方法调用共用的标准记录器
     * @param trustedProxyResolver 宿主认可的客户端地址解析器
     * @param identityContextProvider 服务端身份解析器
     */
    public AnnotatedActionMonitoringInterceptor(ActionEventRecorder recorder,
                                                TrustedProxyResolver trustedProxyResolver,
                                                IdentityContextProvider identityContextProvider) {
        this(recorder, trustedProxyResolver, identityContextProvider, MdcTraceBridge.create(false, "traceId"));
    }

    /**
     * @param recorder 注解与方法调用共用的标准记录器
     * @param trustedProxyResolver 宿主认可的客户端地址解析器
     * @param identityContextProvider 服务端身份解析器
     * @param mdcTraceBridge 可选日志 MDC 链路追踪桥接器
     */
    public AnnotatedActionMonitoringInterceptor(ActionEventRecorder recorder,
                                                TrustedProxyResolver trustedProxyResolver,
                                                IdentityContextProvider identityContextProvider,
                                                MdcTraceBridge mdcTraceBridge) {
        this.recorder = recorder;
        this.trustedProxyResolver = trustedProxyResolver;
        this.identityContextProvider = identityContextProvider;
        this.mdcTraceBridge = mdcTraceBridge;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (action(handler) != null) {
            try {
                RequestMetadataInterceptor.populate(request, trustedProxyResolver, identityContextProvider,
                    mdcTraceBridge, "annotated-action");
            } catch (RuntimeException ignored) {
                // Monitoring is observational and must not block the host action.
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        try {
            MonitorAction action = action(handler);
            Object requestValue = request.getAttribute(RequestMetadataInterceptor.REQUEST_CONTEXT_ATTRIBUTE);
            Object identityValue = request.getAttribute(RequestMetadataInterceptor.IDENTITY_CONTEXT_ATTRIBUTE);
            if (action == null || !(requestValue instanceof MonitoringRequestContext)
                || !(identityValue instanceof IdentityContext)) {
                return;
            }

            SecurityEventResult result = SecurityEventResult.SUCCESS;
            String reasonCode = null;
            if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED
                || response.getStatus() == HttpServletResponse.SC_FORBIDDEN) {
                result = SecurityEventResult.DENIED;
                reasonCode = "HTTP_" + response.getStatus();
            } else if (exception != null) {
                result = SecurityEventResult.FAILURE;
                reasonCode = "HANDLER_EXCEPTION";
            } else if (response.getStatus() >= HttpServletResponse.SC_BAD_REQUEST) {
                result = SecurityEventResult.FAILURE;
                reasonCode = "HTTP_" + response.getStatus();
            }
            recorder.record(action, (MonitoringRequestContext) requestValue, (IdentityContext) identityValue,
                result, reasonCode);
        } catch (RuntimeException ignored) {
            // Monitoring is observational and must not change the completed MVC response.
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
}
