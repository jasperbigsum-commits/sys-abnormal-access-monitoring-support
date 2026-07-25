package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 从当前 Spring MVC Servlet 请求读取 Starter 建立的可信上下文。
 */
public final class ServletMonitoringContextAccessor implements MonitoringContextAccessor {
    @Override
    public MonitoringRequestContext requestContext() {
        return required(MonitoringRequestContext.class, RequestMetadataInterceptor.REQUEST_CONTEXT_ATTRIBUTE,
            "Trusted monitoring request context is unavailable");
    }

    @Override
    public IdentityContext identityContext() {
        return required(IdentityContext.class, RequestMetadataInterceptor.IDENTITY_CONTEXT_ATTRIBUTE,
            "Trusted monitoring identity context is unavailable");
    }

    private static <T> T required(Class<T> type, String attributeName, String message) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            throw new IllegalStateException(message);
        }
        HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
        Object value = request.getAttribute(attributeName);
        if (!type.isInstance(value)) {
            throw new IllegalStateException(message);
        }
        return type.cast(value);
    }
}
