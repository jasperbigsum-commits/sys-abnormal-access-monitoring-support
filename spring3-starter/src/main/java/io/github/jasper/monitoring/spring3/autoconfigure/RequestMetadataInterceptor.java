package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.IdentityContextProvider;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.TrustedProxyResolver;
import io.github.jasper.monitoring.web.FrontendContractHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.web.servlet.HandlerInterceptor;

/** Makes servlet request facts available to host event emitters without coupling to host authentication. */
public final class RequestMetadataInterceptor implements HandlerInterceptor {
    public static final String REQUEST_CONTEXT_ATTRIBUTE = "io.github.jasper.monitoring.request-context";
    public static final String IDENTITY_CONTEXT_ATTRIBUTE = "io.github.jasper.monitoring.identity-context";
    private final TrustedProxyResolver trustedProxyResolver;
    private final IdentityContextProvider identityContextProvider;

    public RequestMetadataInterceptor(TrustedProxyResolver trustedProxyResolver, IdentityContextProvider identityContextProvider) {
        this.trustedProxyResolver = trustedProxyResolver;
        this.identityContextProvider = identityContextProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String directAddress = text(request.getRemoteAddr(), "unknown");
        MonitoringRequestContext requestContext = MonitoringRequestContext.builder()
            .method(text(request.getMethod(), "UNKNOWN"))
            .path(text(request.getRequestURI(), "/"))
            .sourceIp(text(trustedProxyResolver.resolveClientIp(directAddress, request.getHeader("X-Forwarded-For")), directAddress))
            .requestId(firstHeader(request, "X-Request-Id", FrontendContractHeaders.REQUEST_ID, UUID.randomUUID().toString()))
            .traceId(firstHeader(request, "X-Trace-Id", FrontendContractHeaders.TRACE_ID, null))
            .build();
        request.setAttribute(REQUEST_CONTEXT_ATTRIBUTE, requestContext);
        request.setAttribute(IDENTITY_CONTEXT_ATTRIBUTE, resolveIdentity(requestContext));
        return true;
    }

    private IdentityContext resolveIdentity(MonitoringRequestContext requestContext) {
        try {
            IdentityContext identity = identityContextProvider.resolve(requestContext);
            return identity == null ? IdentityContext.anonymous() : identity;
        } catch (RuntimeException ignored) {
            return IdentityContext.anonymous();
        }
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
}
