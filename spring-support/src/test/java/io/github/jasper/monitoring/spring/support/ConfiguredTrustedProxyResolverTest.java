package io.github.jasper.monitoring.spring.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import org.junit.jupiter.api.Test;

class ConfiguredTrustedProxyResolverTest {
    @Test
    void canonicalizesDirectAndForwardedIpv6Literals() {
        ConfiguredTrustedProxyResolver direct = new ConfiguredTrustedProxyResolver(Collections.<String>emptyList());
        ConfiguredTrustedProxyResolver proxy = new ConfiguredTrustedProxyResolver(
            Collections.singletonList("2001:db8::5"));

        assertEquals("2001:db8::1", direct.resolveClientIp("2001:0DB8:0:0:0:0:0:1", null));
        assertEquals("2001:db8::2", proxy.resolveClientIp("2001:0DB8:0:0:0:0:0:5",
            "2001:0DB8:0:0:0:0:0:2"));
        assertEquals("::ffff:c000:201", direct.resolveClientIp("::ffff:192.0.2.1", null));
    }

    @Test
    void neverResolvesHostNames() {
        ConfiguredTrustedProxyResolver resolver = new ConfiguredTrustedProxyResolver(
            Collections.singletonList("localhost"));

        assertNull(resolver.resolveClientIp("localhost", "203.0.113.10"));
        assertNull(resolver.resolveClientIp("192.0.2.1::", null));
    }
}
