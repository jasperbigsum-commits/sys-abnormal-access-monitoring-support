package io.github.jasper.monitoring.spring.support.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class IpAddressLiteralTest {
    @Test
    void canonicalizesEquivalentIpv6FormsWithoutDns() {
        assertEquals("2001:db8::1", IpAddressLiteral.canonicalize("2001:0DB8:0:0:0:0:0:1"));
        assertEquals("::1", IpAddressLiteral.canonicalize("0:0:0:0:0:0:0:1"));
        assertEquals("::ffff:c000:201", IpAddressLiteral.canonicalize("::ffff:192.0.2.1"));
        assertEquals("::ffff:c000:201",
            IpAddressLiteral.canonicalize("0:0:0:0:0:ffff:192.0.2.1"));
    }

    @Test
    void rejectsHostNamesAmbiguousIpv4AndZoneIdentifiers() {
        assertNull(IpAddressLiteral.canonicalize("localhost"));
        assertNull(IpAddressLiteral.canonicalize("203.0.113.010"));
        assertNull(IpAddressLiteral.canonicalize("fe80::1%eth0"));
        assertNull(IpAddressLiteral.canonicalize("192.0.2.1::"));
        assertNull(IpAddressLiteral.canonicalize("1:192.0.2.1::"));
    }
}
