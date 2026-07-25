package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.api.TrustedProxyResolver;
import io.github.jasper.monitoring.spring.support.control.IpControlDecision;
import io.github.jasper.monitoring.spring.support.control.IpAddressLiteral;
import io.github.jasper.monitoring.spring.support.control.IpControlState;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/** Enforces active generic IP controls before an MVC handler is invoked. */
public final class IpControlFilter extends OncePerRequestFilter {
    private final IpControlState state;
    private final TrustedProxyResolver trustedProxyResolver;
    private final List<String> protectedPaths;
    private final List<String> excludedPaths;
    private final Clock clock;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public IpControlFilter(IpControlState state, TrustedProxyResolver trustedProxyResolver,
                           List<String> protectedPaths, List<String> excludedPaths, Clock clock) {
        if (state == null || trustedProxyResolver == null || clock == null) {
            throw new IllegalArgumentException("state, trustedProxyResolver and clock are required");
        }
        this.state = state;
        this.trustedProxyResolver = trustedProxyResolver;
        this.protectedPaths = immutableCopy(protectedPaths);
        this.excludedPaths = immutableCopy(excludedPaths);
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = applicationPath(request);
        if (!matchesAny(protectedPaths, path) || matchesAny(excludedPaths, path)) {
            filterChain.doFilter(request, response);
            return;
        }
        String clientIp = IpAddressLiteral.canonicalize(trustedProxyResolver.resolveClientIp(request.getRemoteAddr(),
            request.getHeader("X-Forwarded-For")));
        if (clientIp == null) {
            clientIp = IpAddressLiteral.canonicalize(request.getRemoteAddr());
        }
        if (clientIp == null) {
            filterChain.doFilter(request, response);
            return;
        }
        IpControlDecision decision = state.check(clientIp, clock.instant());
        if (decision.isDenied()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (decision.isRateLimited()) {
            response.setStatus(429);
            response.setHeader("Retry-After", Long.toString(ceilSeconds(decision.getRetryAfter())));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean matchesAny(List<String> patterns, String path) {
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private static String applicationPath(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }

    private static long ceilSeconds(Duration duration) {
        return duration.getSeconds() + (duration.getNano() == 0 ? 0 : 1);
    }

    private static List<String> immutableCopy(List<String> values) {
        if (values == null) {
            throw new IllegalArgumentException("path patterns are required");
        }
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
