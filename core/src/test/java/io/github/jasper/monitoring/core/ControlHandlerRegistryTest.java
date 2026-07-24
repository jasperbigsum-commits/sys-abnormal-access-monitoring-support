package io.github.jasper.monitoring.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.ControlStatus;
import io.github.jasper.monitoring.core.application.control.ControlHandlerRegistry;
import io.github.jasper.monitoring.core.application.control.DefaultControlActionTrigger;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.port.ControlHandler;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ControlHandlerRegistryTest {

    @Test
    void resolvesHostHandlerBeforeTheDefaultTriggerForTheSameAction() {
        ControlHandler host = new ControlHandler() {
            @Override
            public boolean supports(ControlActionType action) {
                return action == ControlActionType.DENY;
            }

            @Override
            public ControlExecution execute(ControlCommand command) {
                return ControlExecution.succeeded(command.getIdempotencyKey());
            }
        };
        ControlHandlerRegistry registry = new ControlHandlerRegistry(Arrays.asList(host),
            DefaultControlActionTrigger.defaults());

        assertSame(host, registry.find(ControlActionType.DENY).get());
        assertFalse(registry.isEmpty());
    }

    @Test
    void fallsBackToTheDefaultTriggerForAnUnimplementedAction() {
        ControlHandlerRegistry registry = new ControlHandlerRegistry(Collections.<ControlHandler>emptyList(),
            DefaultControlActionTrigger.defaults());
        ControlCommand command = new ControlCommand("alert-1:RATE_LIMIT", "alert-1", "ip:203.0.113.8",
            ControlActionType.RATE_LIMIT, Instant.parse("2026-07-24T00:00:00Z"));

        ControlExecution execution = registry.find(ControlActionType.RATE_LIMIT).get().execute(command);

        assertEquals(ControlStatus.SKIPPED, execution.getStatus());
        assertEquals("DEFAULT_TRIGGER_REQUIRES_HOST_HANDLER:RATE_LIMIT", execution.getFailureReason());
    }

    @Test
    void defaultTriggersDoNotSatisfyTheEnforceModeHostHandlerRequirement() {
        ControlHandlerRegistry registry = new ControlHandlerRegistry(Collections.<ControlHandler>emptyList(),
            DefaultControlActionTrigger.defaults());

        assertTrue(registry.isEmpty());
        assertFalse(registry.find(ControlActionType.RECORD).isPresent());
    }
}
