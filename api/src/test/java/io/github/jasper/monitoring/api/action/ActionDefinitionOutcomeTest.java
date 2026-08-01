package io.github.jasper.monitoring.api.action;

import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionDefinitionOutcomeTest {
    @Test
    void resolvesEventTypeFromTheFinalOutcome() {
        ActionDefinition definition = ActionDefinition.builder("auth:login")
            .eventType(SecurityEventType.LOGIN_FAILURE)
            .eventTypeFor(SecurityEventResult.SUCCESS, SecurityEventType.LOGIN_SUCCESS)
            .resourceType("authentication")
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY)
            .build();

        assertEquals(SecurityEventType.LOGIN_SUCCESS,
            definition.resolveEventType(SecurityEventResult.SUCCESS));
        assertEquals(SecurityEventType.LOGIN_FAILURE,
            definition.resolveEventType(SecurityEventResult.DENIED));
        assertEquals(SecurityEventType.LOGIN_FAILURE,
            definition.resolveEventType(SecurityEventResult.FAILURE));
    }
}
