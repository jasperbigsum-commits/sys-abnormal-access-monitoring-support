package io.github.jasper.monitoring.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SecurityEventDraftTest {

    @Test
    void distinguishesUnknownCountsFromExplicitZeroCounts() {
        SecurityEventDraft unknown = requiredDraft().build();
        SecurityEventDraft explicitZero = requiredDraft().dataCount(0).latencyMs(0).build();

        assertFalse(unknown.hasDataCount());
        assertFalse(unknown.hasLatencyMs());
        assertEquals(0L, unknown.getDataCount());
        assertEquals(0L, unknown.getLatencyMs());
        assertTrue(explicitZero.hasDataCount());
        assertTrue(explicitZero.hasLatencyMs());
        assertEquals(0L, explicitZero.getDataCount());
        assertEquals(0L, explicitZero.getLatencyMs());
    }

    @Test
    void rejectsAttributeKeysThatDuplicateAfterCaseNormalization() {
        assertThrows(IllegalArgumentException.class, () -> requiredDraft()
            .attribute("Sensitivity", "HIGH")
            .attribute("sensitivity", "LOW")
            .build());
    }

    @Test
    void rejectsRepeatedCanonicalAttributeKeysBeforeMapReplacement() {
        assertThrows(IllegalArgumentException.class, () -> requiredDraft()
            .attribute("sensitivity", "HIGH")
            .attribute("sensitivity", "LOW")
            .build());
    }

    @Test
    void normalizesStaticActionAttributeKeysAndRejectsCaseInsensitiveDuplicates() {
        MonitorActionDefinition definition = MonitorActionDefinition.builder("report:export")
            .attribute("Sensitivity", "HIGH")
            .build();

        assertEquals("HIGH", definition.getAttributes().get("sensitivity"));
        assertThrows(IllegalArgumentException.class, () -> MonitorActionDefinition.builder("report:export")
            .attribute("Sensitivity", "HIGH")
            .attribute("sensitivity", "LOW")
            .build());
    }

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

    private static SecurityEventDraft.Builder requiredDraft() {
        return SecurityEventDraft.builder()
            .eventType(SecurityEventType.QUERY)
            .action("QUERY")
            .result(SecurityEventResult.SUCCESS)
            .sourceIp("203.0.113.8")
            .requestId("req-1")
            .occurredAt(Instant.parse("2026-07-22T00:00:00Z"));
    }
}
