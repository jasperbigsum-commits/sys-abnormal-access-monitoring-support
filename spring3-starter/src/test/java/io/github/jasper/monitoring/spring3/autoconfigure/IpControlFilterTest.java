package io.github.jasper.monitoring.spring3.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.spring.support.ConfiguredTrustedProxyResolver;
import io.github.jasper.monitoring.spring.support.control.GenericIpControlHandler;
import io.github.jasper.monitoring.spring.support.control.LocalIpControlState;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class IpControlFilterTest {
    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");

    @Test
    void rejectsMissingDependenciesWithAStableCode() {
        MonitoringValidationException exception = assertThrows(MonitoringValidationException.class,
            () -> new IpControlFilter(null, new ConfiguredTrustedProxyResolver(Collections.<String>emptyList()),
                Collections.singletonList("/api/**"), Collections.<String>emptyList(),
                Clock.fixed(NOW, ZoneOffset.UTC)));

        assertEquals(MonitoringErrorCode.REQUIRED_FIELD_MISSING, exception.getErrorCode());
    }

    @Test
    void returnsBare403ForAProtectedDeniedIp() throws Exception {
        Fixture fixture = fixture(1, Duration.ofSeconds(30));
        fixture.activate(ControlActionType.DENY, "203.0.113.10");

        Exchange exchange = fixture.exchange("/api/report", "203.0.113.10", null);

        assertThat(exchange.response.getStatus()).isEqualTo(403);
        assertThat(exchange.response.getContentAsByteArray()).isEmpty();
        assertThat(exchange.chainCalled).isFalse();
    }

    @Test
    void returns429WithCeilingRetryAfterAfterRateLimitPermitIsConsumed() throws Exception {
        Fixture fixture = fixture(1, Duration.ofMillis(1500));
        fixture.activate(ControlActionType.RATE_LIMIT, "203.0.113.10");
        assertThat(fixture.exchange("/api/report", "203.0.113.10", null).chainCalled).isTrue();

        Exchange exchange = fixture.exchange("/api/report", "203.0.113.10", null);

        assertThat(exchange.response.getStatus()).isEqualTo(429);
        assertThat(exchange.response.getHeader("Retry-After")).isEqualTo("2");
        assertThat(exchange.response.getContentAsByteArray()).isEmpty();
        assertThat(exchange.chainCalled).isFalse();
    }

    @Test
    void bypassesUnprotectedAndExcludedPaths() throws Exception {
        Fixture fixture = fixture(1, Duration.ofSeconds(30));
        fixture.activate(ControlActionType.DENY, "203.0.113.10");

        assertThat(fixture.exchange("/public", "203.0.113.10", null).chainCalled).isTrue();
        assertThat(fixture.exchange("/api/health", "203.0.113.10", null).chainCalled).isTrue();
    }

    @Test
    void ignoresForwardedForFromAnUntrustedPeerAndUsesItFromATrustedProxy() throws Exception {
        Fixture fixture = fixture(1, Duration.ofSeconds(30));
        fixture.activate(ControlActionType.DENY, "203.0.113.10");

        assertThat(fixture.exchange("/api/report", "198.51.100.7", "203.0.113.10").chainCalled).isTrue();
        assertThat(fixture.exchange("/api/report", "192.0.2.5", "203.0.113.10").response.getStatus())
            .isEqualTo(403);
    }

    @Test
    void allowsARequestAfterTheIpControlExpires() throws Exception {
        LocalIpControlState state = new LocalIpControlState(2, 1, Duration.ofSeconds(10));
        state.activate("deny-expiring", "203.0.113.10", ControlActionType.DENY,
            NOW.plusSeconds(1), NOW);
        Clock afterExpiry = Clock.fixed(NOW.plusSeconds(2), ZoneOffset.UTC);
        IpControlFilter filter = new IpControlFilter(state,
            new ConfiguredTrustedProxyResolver(Collections.<String>emptyList()),
            Arrays.asList("/api/**"), Collections.<String>emptyList(), afterExpiry);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/report");
        request.setRemoteAddr("203.0.113.10");
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(request, new MockHttpServletResponse(),
            (ignoredRequest, ignoredResponse) -> called.set(true));

        assertThat(called).isTrue();
    }

    @Test
    void blocksEquivalentExpandedIpv6ForACanonicalControlKey() throws Exception {
        Fixture fixture = fixture(1, Duration.ofSeconds(30));
        fixture.activate(ControlActionType.DENY, "2001:db8::1");

        Exchange exchange = fixture.exchange("/api/report", "2001:0DB8:0:0:0:0:0:1", null);

        assertThat(exchange.response.getStatus()).isEqualTo(403);
        assertThat(exchange.chainCalled).isFalse();

        Fixture mapped = fixture(1, Duration.ofSeconds(30));
        mapped.activate(ControlActionType.DENY, "::ffff:c000:201");
        Exchange mappedExchange = mapped.exchange("/api/report", "0:0:0:0:0:ffff:192.0.2.1", null);
        assertThat(mappedExchange.response.getStatus()).isEqualTo(403);
        assertThat(mappedExchange.chainCalled).isFalse();
    }

    @Test
    void protectsMvcLookupPathWithContextAndMatrixParameters() throws Exception {
        Fixture fixture = fixture(1, Duration.ofSeconds(30));
        fixture.activate(ControlActionType.DENY, "203.0.113.10");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/app/api;x/report");
        request.setContextPath("/app");
        request.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        fixture.filter.doFilter(request, response,
            (ignoredRequest, ignoredResponse) -> called.set(true));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(called).isFalse();

        MockHttpServletRequest encoded = new MockHttpServletRequest("GET", "/api%2Freport");
        encoded.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse encodedResponse = new MockHttpServletResponse();
        fixture.filter.doFilter(encoded, encodedResponse,
            (ignoredRequest, ignoredResponse) -> called.set(true));
        assertThat(encodedResponse.getStatus()).isEqualTo(403);
    }

    private static Fixture fixture(int permits, Duration window) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        LocalIpControlState state = new LocalIpControlState(10, permits, window);
        GenericIpControlHandler handler = new GenericIpControlHandler(state, Collections.singleton("RULE-IP"),
            Duration.ofMinutes(5), clock);
        IpControlFilter filter = new IpControlFilter(state,
            new ConfiguredTrustedProxyResolver(Collections.singletonList("192.0.2.5")),
            Arrays.asList("/api/**"), Arrays.asList("/api/health"), clock);
        return new Fixture(handler, filter);
    }

    private static final class Fixture {
        private final GenericIpControlHandler handler;
        private final IpControlFilter filter;

        private Fixture(GenericIpControlHandler handler, IpControlFilter filter) {
            this.handler = handler;
            this.filter = filter;
        }

        private void activate(ControlActionType action, String ip) {
            handler.execute(new ControlCommand("test-system", "control-" + action, "alert-1", "ip:" + ip, action,
                NOW.plusSeconds(60), "RULE-IP"));
        }

        private Exchange exchange(String path, String remoteAddress, String forwardedFor) throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setRemoteAddr(remoteAddress);
            if (forwardedFor != null) {
                request.addHeader("X-Forwarded-For", forwardedFor);
            }
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean called = new AtomicBoolean();
            FilterChain chain = (ignoredRequest, ignoredResponse) -> called.set(true);
            filter.doFilter(request, response, chain);
            return new Exchange(response, called.get());
        }
    }

    private static final class Exchange {
        private final MockHttpServletResponse response;
        private final boolean chainCalled;

        private Exchange(MockHttpServletResponse response, boolean chainCalled) {
            this.response = response;
            this.chainCalled = chainCalled;
        }
    }
}
