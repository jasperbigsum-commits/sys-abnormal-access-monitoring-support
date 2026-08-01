package io.github.jasper.monitoring.api.event;

import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionOutcomeTest {
    @Test
    void ownsStableFailureClassificationAndLatency() {
        ActionOutcome outcome = ActionOutcome.failure(
            BuiltInReasonCodes.Action.INVOCATION_FAILED,
            FailureClass.INFRASTRUCTURE, 23L);
        assertEquals(FailureClass.INFRASTRUCTURE, outcome.getFailureClass());
        assertEquals(23L, outcome.getLatencyMs());
    }

    @Test
    void rejectsNegativeLatencyAndKeepsNonFailureUnclassified() {
        assertThrows(IllegalArgumentException.class, () -> ActionOutcome.success(-1L));
        assertNull(ActionOutcome.denied(
            BuiltInReasonCodes.Authorization.RESOURCE_SCOPE_DENIED, 3L).getFailureClass());
        assertThrows(NullPointerException.class, () -> ActionOutcome.denied(null, 0L));
    }
}
