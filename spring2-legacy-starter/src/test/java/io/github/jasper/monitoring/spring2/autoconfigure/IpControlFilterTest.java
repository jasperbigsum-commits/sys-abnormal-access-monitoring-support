package io.github.jasper.monitoring.spring2.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.TrustedProxyResolver;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import io.github.jasper.monitoring.spring.support.ConfiguredTrustedProxyResolver;
import io.github.jasper.monitoring.spring.support.control.IpControlDecision;
import io.github.jasper.monitoring.spring.support.control.IpControlState;
import io.github.jasper.monitoring.spring.support.control.LocalIpControlState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import javax.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class IpControlFilterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-25T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void rejectsMissingDependenciesWithAStableCode() {
        MonitoringValidationException exception = assertThrows(MonitoringValidationException.class,
            () -> new IpControlFilter(null, directAddressResolver(), Collections.singletonList("/api/**"),
                Collections.<String>emptyList(), CLOCK));

        assertEquals(MonitoringErrorCode.REQUIRED_FIELD_MISSING, exception.getErrorCode());
    }

    @Test
    void returnsBare403ForDeniedIpOnAProtectedPath() throws Exception {
        RecordingState state = new RecordingState(IpControlDecision.denied());
        IpControlFilter filter = filter(state, directAddressResolver());
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/api/report", "203.0.113.10"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeaderNames()).isEmpty();
        Mockito.verifyZeroInteractions(chain);
        assertThat(state.checkedIp).isEqualTo("203.0.113.10");
    }

    @Test
    void returnsBare429WithCeilingRetryAfterSeconds() throws Exception {
        RecordingState state = new RecordingState(IpControlDecision.rateLimited(Duration.ofMillis(1001)));
        IpControlFilter filter = filter(state, directAddressResolver());
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/api/report", "203.0.113.10"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("2");
        assertThat(response.getContentAsByteArray()).isEmpty();
        Mockito.verifyZeroInteractions(chain);
    }

    @Test
    void bypassesExcludedAndUnprotectedPaths() throws Exception {
        RecordingState state = new RecordingState(IpControlDecision.denied());
        IpControlFilter filter = filter(state, directAddressResolver());
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request("/api/health", "203.0.113.10"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("/public", "203.0.113.10"), new MockHttpServletResponse(), chain);

        Mockito.verify(chain, Mockito.times(2)).doFilter(Mockito.any(), Mockito.any());
        assertThat(state.checkedIp).isNull();
    }

    @Test
    void delegatesClientIpSelectionToTheTrustedProxyResolver() throws Exception {
        RecordingState state = new RecordingState(IpControlDecision.allowed());
        TrustedProxyResolver resolver = Mockito.mock(TrustedProxyResolver.class);
        Mockito.when(resolver.resolveClientIp("10.0.0.5", "198.51.100.7, 10.0.0.4"))
            .thenReturn("198.51.100.7");
        IpControlFilter filter = filter(state, resolver);
        FilterChain chain = Mockito.mock(FilterChain.class);
        MockHttpServletRequest request = request("/api/report", "10.0.0.5");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.4");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(state.checkedIp).isEqualTo("198.51.100.7");
        Mockito.verify(chain).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    void ignoresForwardedForFromAnUntrustedPeer() throws Exception {
        RecordingState state = new RecordingState(IpControlDecision.allowed());
        IpControlFilter filter = filter(state,
            new ConfiguredTrustedProxyResolver(Collections.singletonList("192.0.2.5")));
        MockHttpServletRequest request = request("/api/report", "198.51.100.7");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        filter.doFilter(request, new MockHttpServletResponse(), Mockito.mock(FilterChain.class));

        assertThat(state.checkedIp).isEqualTo("198.51.100.7");
    }

    @Test
    void allowsARequestAfterTheIpControlExpires() throws Exception {
        LocalIpControlState state = new LocalIpControlState(2, 1, Duration.ofSeconds(10));
        Instant now = Instant.parse("2026-07-25T00:00:00Z");
        state.activate("deny-expiring", "203.0.113.10", ControlActionType.DENY,
            now.plusSeconds(1), now);
        IpControlFilter filter = new IpControlFilter(state, directAddressResolver(),
            Arrays.asList("/api/**"), Collections.<String>emptyList(),
            Clock.fixed(now.plusSeconds(2), ZoneOffset.UTC));
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(request("/api/report", "203.0.113.10"),
            new MockHttpServletResponse(), chain);

        Mockito.verify(chain).doFilter(Mockito.any(), Mockito.any());
    }

    @Test
    void canonicalizesEquivalentIpv6BeforeCheckingState() throws Exception {
        RecordingState state = new RecordingState(IpControlDecision.denied());
        IpControlFilter filter = filter(state, directAddressResolver());

        filter.doFilter(request("/api/report", "2001:0DB8:0:0:0:0:0:1"),
            new MockHttpServletResponse(), Mockito.mock(FilterChain.class));

        assertThat(state.checkedIp).isEqualTo("2001:db8::1");

        filter.doFilter(request("/api/report", "::ffff:192.0.2.1"),
            new MockHttpServletResponse(), Mockito.mock(FilterChain.class));
        assertThat(state.checkedIp).isEqualTo("::ffff:c000:201");
    }

    @Test
    void protectsMvcLookupPathWithContextAndMatrixParameters() throws Exception {
        RecordingState state = new RecordingState(IpControlDecision.denied());
        IpControlFilter filter = filter(state, directAddressResolver());
        MockHttpServletRequest request = request("/app/api;x/report", "203.0.113.10");
        request.setContextPath("/app");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, Mockito.mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(403);

        MockHttpServletResponse encodedResponse = new MockHttpServletResponse();
        filter.doFilter(request("/api%2Freport", "203.0.113.10"), encodedResponse,
            Mockito.mock(FilterChain.class));
        assertThat(encodedResponse.getStatus()).isEqualTo(403);
    }

    private static IpControlFilter filter(IpControlState state, TrustedProxyResolver resolver) {
        return new IpControlFilter(state, resolver, Arrays.asList("/api/**"),
            Collections.singletonList("/api/health"), CLOCK);
    }

    private static TrustedProxyResolver directAddressResolver() {
        return (remoteAddress, forwardedFor) -> remoteAddress;
    }

    private static MockHttpServletRequest request(String path, String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private static final class RecordingState implements IpControlState {
        private final IpControlDecision decision;
        private String checkedIp;

        private RecordingState(IpControlDecision decision) {
            this.decision = decision;
        }

        @Override
        public ActivationResult activate(String idempotencyKey, String canonicalIp,
                                         io.github.jasper.monitoring.api.ControlActionType action,
                                         Instant expiresAt, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IpControlDecision check(String canonicalIp, Instant now) {
            checkedIp = canonicalIp;
            return decision;
        }
    }
}
