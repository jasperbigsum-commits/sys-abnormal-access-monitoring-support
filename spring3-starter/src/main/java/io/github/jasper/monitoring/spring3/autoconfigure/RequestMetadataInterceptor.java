package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.IdentityContextProvider;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.TrustedProxyResolver;
import io.github.jasper.monitoring.spring.support.MdcTraceBridge;
import io.github.jasper.monitoring.web.FrontendContractHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 建立 Servlet 请求的可信上下文，并在可用时统一监测事件与日志 MDC 的追踪标识。
 *
 * <p>该拦截器不依赖宿主认证实现。身份、来源 IP 和请求标识均由服务端适配器建立；MDC 仅用于日志关联，
 * 关闭请求时会恢复原线程上下文，避免线程复用串链。</p>
 */
public final class RequestMetadataInterceptor implements HandlerInterceptor {
    /** Servlet 请求属性：可信、不可变的监测请求上下文。 */
    public static final String REQUEST_CONTEXT_ATTRIBUTE = "io.github.jasper.monitoring.request-context";
    /** Servlet 请求属性：由宿主身份提供器建立的身份上下文。 */
    public static final String IDENTITY_CONTEXT_ATTRIBUTE = "io.github.jasper.monitoring.identity-context";
    private static final String MDC_SCOPE_ATTRIBUTE = "io.github.jasper.monitoring.mdc-scope";
    private static final String MDC_SCOPE_OWNER_ATTRIBUTE = "io.github.jasper.monitoring.mdc-scope-owner";
    private final TrustedProxyResolver trustedProxyResolver;
    private final IdentityContextProvider identityContextProvider;
    private final MdcTraceBridge mdcTraceBridge;

    /**
     * 创建不操作 MDC 的请求元数据拦截器，保留已有宿主的构造方式。
     *
     * @param trustedProxyResolver 仅在直连代理可信时解析真实客户端 IP 的解析器
     * @param identityContextProvider 宿主服务端身份快照提供器
     */
    public RequestMetadataInterceptor(TrustedProxyResolver trustedProxyResolver,
                                      IdentityContextProvider identityContextProvider) {
        this(trustedProxyResolver, identityContextProvider, MdcTraceBridge.create(false, "traceId"));
    }

    /**
     * @param trustedProxyResolver 仅在直连代理可信时解析真实客户端 IP 的解析器
     * @param identityContextProvider 宿主服务端身份快照提供器
     * @param mdcTraceBridge 可选日志 MDC 链路追踪桥接器
     */
    public RequestMetadataInterceptor(TrustedProxyResolver trustedProxyResolver,
                                      IdentityContextProvider identityContextProvider,
                                      MdcTraceBridge mdcTraceBridge) {
        this.trustedProxyResolver = trustedProxyResolver;
        this.identityContextProvider = identityContextProvider;
        this.mdcTraceBridge = mdcTraceBridge;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            populate(request, trustedProxyResolver, identityContextProvider, mdcTraceBridge, "request-metadata");
        } catch (RuntimeException ignored) {
            // Monitoring metadata is supplemental and must not block the host request.
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        clearMdcScope(request, "request-metadata");
    }

    /**
     * 当其他适配器尚未建立上下文时，创建可信请求和身份快照。
     * 包可见是为了让注解动作拦截器复用完全相同的信任边界。
     */
    static void populate(HttpServletRequest request, TrustedProxyResolver trustedProxyResolver,
                         IdentityContextProvider identityContextProvider) {
        populate(request, trustedProxyResolver, identityContextProvider, MdcTraceBridge.create(false, "traceId"),
            "legacy");
    }

    /** 在给定 MDC 作用域归属下建立可信上下文。 */
    static void populate(HttpServletRequest request, TrustedProxyResolver trustedProxyResolver,
                         IdentityContextProvider identityContextProvider, MdcTraceBridge mdcTraceBridge,
                         String mdcScopeOwner) {
        Object existingRequest = request.getAttribute(REQUEST_CONTEXT_ATTRIBUTE);
        MonitoringRequestContext requestContext;
        if (existingRequest instanceof MonitoringRequestContext) {
            requestContext = (MonitoringRequestContext) existingRequest;
        } else {
            String directAddress = text(request.getRemoteAddr(), "unknown");
            String traceId = traceId(request, mdcTraceBridge);
            requestContext = MonitoringRequestContext.builder()
                .method(text(request.getMethod(), "UNKNOWN"))
                .path(text(request.getRequestURI(), "/"))
                .sourceIp(text(trustedProxyResolver.resolveClientIp(directAddress, request.getHeader("X-Forwarded-For")), directAddress))
                .requestId(firstHeader(request, "X-Request-Id", FrontendContractHeaders.REQUEST_ID, UUID.randomUUID().toString()))
                .traceId(traceId)
                .build();
            request.setAttribute(REQUEST_CONTEXT_ATTRIBUTE, requestContext);
        }
        if (blank(requestContext.getTraceId())) {
            requestContext = withTraceId(requestContext, traceId(request, mdcTraceBridge));
            request.setAttribute(REQUEST_CONTEXT_ATTRIBUTE, requestContext);
        }
        bindMdcIfAbsent(request, requestContext.getTraceId(), mdcTraceBridge, mdcScopeOwner);
        if (!(request.getAttribute(IDENTITY_CONTEXT_ATTRIBUTE) instanceof IdentityContext)) {
            request.setAttribute(IDENTITY_CONTEXT_ATTRIBUTE, resolveIdentity(requestContext, identityContextProvider));
        }
    }

    /** 仅由创建该作用域的拦截器恢复 MDC，避免多个拦截器重复清理。 */
    static void clearMdcScope(HttpServletRequest request, String mdcScopeOwner) {
        if (!mdcScopeOwner.equals(request.getAttribute(MDC_SCOPE_OWNER_ATTRIBUTE))) {
            return;
        }
        Object scope = request.getAttribute(MDC_SCOPE_ATTRIBUTE);
        if (scope instanceof MdcTraceBridge.Scope) {
            ((MdcTraceBridge.Scope) scope).close();
        }
        request.removeAttribute(MDC_SCOPE_ATTRIBUTE);
        request.removeAttribute(MDC_SCOPE_OWNER_ATTRIBUTE);
    }

    private static IdentityContext resolveIdentity(MonitoringRequestContext requestContext,
                                                   IdentityContextProvider identityContextProvider) {
        try {
            IdentityContext identity = identityContextProvider.resolve(requestContext);
            return identity == null ? IdentityContext.anonymous() : identity;
        } catch (RuntimeException ignored) {
            return IdentityContext.anonymous();
        }
    }

    private static void bindMdcIfAbsent(HttpServletRequest request, String traceId, MdcTraceBridge mdcTraceBridge,
                                        String mdcScopeOwner) {
        if (request.getAttribute(MDC_SCOPE_ATTRIBUTE) == null) {
            request.setAttribute(MDC_SCOPE_ATTRIBUTE, mdcTraceBridge.bind(traceId));
            request.setAttribute(MDC_SCOPE_OWNER_ATTRIBUTE, mdcScopeOwner);
        }
    }

    private static MonitoringRequestContext withTraceId(MonitoringRequestContext value, String traceId) {
        MonitoringRequestContext.Builder builder = MonitoringRequestContext.builder()
            .method(value.getMethod()).path(value.getPath()).sourceIp(value.getSourceIp())
            .requestId(value.getRequestId()).traceId(traceId);
        for (Map.Entry<String, String> header : value.getTrustedHeaders().entrySet()) {
            builder.trustedHeader(header.getKey(), header.getValue());
        }
        return builder.build();
    }

    private static String traceId(HttpServletRequest request, MdcTraceBridge mdcTraceBridge) {
        String header = firstHeader(request, "X-Trace-Id", FrontendContractHeaders.TRACE_ID, null);
        if (!blank(header)) {
            return header;
        }
        String fromMdc = mdcTraceBridge.currentTraceId();
        return blank(fromMdc) ? UUID.randomUUID().toString() : fromMdc;
    }

    private static String firstHeader(HttpServletRequest request, String primary, String secondary, String fallback) {
        String value = request.getHeader(primary);
        if (value == null || value.trim().isEmpty()) {
            value = request.getHeader(secondary);
        }
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String text(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
