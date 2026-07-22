package io.github.jasper.monitoring.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class FrontendSignalTest {

    @Test
    void rejectsClientSuppliedIdentityAndUnknownMetadata() {
        assertThrows(IllegalArgumentException.class, () -> FrontendSignal.builder()
            .clientEventId("client-1")
            .occurredAt(Instant.parse("2026-07-22T00:00:00Z"))
            .requestId("request-1")
            .route("/orders/1")
            .action("VIEW")
            .attribute("user_id", "alice")
            .build());
    }

    @Test
    void mapsOnlyServerAuthoritativeIdentityIntoSecurityEvent() {
        FrontendSignal signal = FrontendSignal.builder()
            .clientEventId("client-1")
            .occurredAt(Instant.parse("2026-07-22T00:00:00Z"))
            .requestId("request-1")
            .traceId("trace-1")
            .route("/orders/1")
            .action("VIEW")
            .deviceIdHash("sha256:device")
            .attribute("feature", "order-detail")
            .build();

        FrontendServerContext context = FrontendServerContext.builder()
            .sourceIp("203.0.113.11")
            .userId("server-alice")
            .sessionIdHash("sha256:session")
            .build();

        assertEquals("server-alice", FrontendSignalMapper.toDraft(signal, context).getUserId());
        assertEquals("203.0.113.11", FrontendSignalMapper.toDraft(signal, context).getSourceIp());
        assertEquals("order-detail", FrontendSignalMapper.toDraft(signal, context).getAttribute("feature"));
    }
}
