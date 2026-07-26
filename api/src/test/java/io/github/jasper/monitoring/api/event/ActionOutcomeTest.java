package io.github.jasper.monitoring.api.event;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionOutcomeTest {
    @Test
    void ownsStableFailureClassificationAndLatency() {
        ActionOutcome outcome = ActionOutcome.failure("storage-timeout",
            ActionOutcome.ExceptionClassification.INFRASTRUCTURE, 23L);
        assertEquals(ActionOutcome.ExceptionClassification.INFRASTRUCTURE,
            outcome.getExceptionClassification());
        assertEquals(23L, outcome.getLatencyMs());
    }

    @Test
    void rejectsNegativeLatencyAndKeepsNonFailureUnclassified() {
        assertThrows(IllegalArgumentException.class, () -> ActionOutcome.success(-1L));
        assertNull(ActionOutcome.denied("scope-denied", 3L).getExceptionClassification());
    }
}
