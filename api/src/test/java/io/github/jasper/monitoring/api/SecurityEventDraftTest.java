package io.github.jasper.monitoring.api;

import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
    void rejectsMapAttributeKeysThatDuplicateAfterCaseNormalization() {
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        attributes.put("Sensitivity", "HIGH");
        attributes.put("sensitivity", "LOW");

        assertThrows(IllegalArgumentException.class, () -> requiredDraft().attributes(attributes).build());
    }

    @Test
    void normalizesAttributeLookupKeysWithoutRejectingProbes() {
        SecurityEventDraft draft = requiredDraft().attribute("Sensitivity", "HIGH").build();

        assertEquals("HIGH", draft.getAttribute("Sensitivity"));
        assertEquals("HIGH", draft.getAttribute("SENSITIVITY"));
        assertNull(draft.getAttribute(null));
        assertNull(draft.getAttribute(" "));
        assertNull(draft.getAttribute("password"));
        assertNull(draft.getAttribute("unknown"));
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
        MonitoringValidationException exception = assertThrows(MonitoringValidationException.class,
            () -> SecurityEventDraft.builder()
            .eventType(SecurityEventType.LOGIN_FAILURE)
            .action("LOGIN")
            .result(SecurityEventResult.FAILURE)
            .sourceIp("203.0.113.8")
            .requestId("req-1")
            .occurredAt(Instant.parse("2026-07-22T00:00:00Z"))
            .attribute("password", "not-allowed")
            .build());

        assertEquals(MonitoringErrorCode.UNSAFE_EVENT_ATTRIBUTE, exception.getErrorCode());
        assertTrue(exception instanceof IllegalArgumentException);
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
