package io.github.jasper.monitoring.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SecurityEventDraftTest {

    @Test
    void rejectsForbiddenMetadataKeys() {
        assertThrows(IllegalArgumentException.class, () -> SecurityEventDraft.builder()
            .eventType(SecurityEventType.LOGIN_FAILURE)
            .action("LOGIN")
            .result(SecurityEventResult.FAILURE)
            .sourceIp("203.0.113.8")
            .requestId("req-1")
            .occurredAt(Instant.parse("2026-07-22T00:00:00Z"))
            .attribute("password", "not-allowed")
            .build());
    }

    @Test
    void normalizesLogInjectionCharactersInSafeFields() {
        SecurityEventDraft draft = SecurityEventDraft.builder()
            .eventType(SecurityEventType.QUERY)
            .action("QUERY\nINJECTED")
            .result(SecurityEventResult.SUCCESS)
            .sourceIp("203.0.113.8")
            .requestId("req-1\r\nnext")
            .occurredAt(Instant.parse("2026-07-22T00:00:00Z"))
            .build();

        assertEquals("QUERY INJECTED", draft.getAction());
        assertEquals("req-1 next", draft.getRequestId());
    }
}
