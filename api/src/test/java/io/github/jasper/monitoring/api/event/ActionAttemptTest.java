package io.github.jasper.monitoring.api.event;

import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionAttemptTest {
    @Test
    void enforcesFactsDecisionAndCompletionOrder() {
        ActionAttempt attempt = ActionAttempt.start(TestAction.class);
        assertEquals(ActionAttempt.Status.CREATED, attempt.getStatus());
        assertThrows(IllegalStateException.class, () -> attempt.complete(ActionOutcome.success(0L)));
        attempt.factsReady(ActionFacts.builder().build());
        attempt.decided(ActionDecision.allow());
        attempt.complete(ActionOutcome.success(0L));
        assertEquals(ActionAttempt.Status.COMPLETED_SUCCESS, attempt.getStatus());
    }

    static final class TestAction implements ActionType { }
}
