package io.github.jasper.monitoring.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.ControlStatus;
import io.github.jasper.monitoring.api.ControlTrigger;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import io.github.jasper.monitoring.core.application.control.AnnotatedControlHandler;
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
    void rejectsTwoExplicitHandlersForTheSameAction() {
        assertThrows(MonitoringConfigurationException.class, () ->
            new ControlHandlerRegistry(Arrays.asList(
                handlerFor(ControlActionType.DENY), handlerFor(ControlActionType.DENY))));
    }

    @Test
    void rejectsExplicitAndAnnotatedHandlersForTheSameAction() {
        assertThrows(MonitoringConfigurationException.class, () ->
            new ControlHandlerRegistry(Arrays.asList(handlerFor(ControlActionType.DENY),
                new AnnotatedControlHandler(new AnnotatedDeny()))));
    }

    @Test
    void rejectsGenericAndExplicitHandlersForTheSameAction() {
        assertThrows(MonitoringConfigurationException.class, () ->
            new ControlHandlerRegistry(Arrays.asList(handlerFor(ControlActionType.RATE_LIMIT)),
                Arrays.asList(handlerFor(ControlActionType.RATE_LIMIT)),
                DefaultControlActionTrigger.defaults()));
    }

    @Test
    void rejectsInvalidAnnotatedHandlerSignature() {
        assertThrows(MonitoringValidationException.class, () ->
            new AnnotatedControlHandler(new InvalidAnnotatedDeny()));
    }

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

    @Test
    void resolvesDistinctHostAndGenericBindingsBeforeDefault() {
        ControlHandler host = handlerFor(ControlActionType.DENY);
        ControlHandler generic = handlerFor(ControlActionType.RATE_LIMIT);
        ControlHandlerRegistry registry = new ControlHandlerRegistry(Arrays.asList(host),
            Arrays.asList(generic), DefaultControlActionTrigger.defaults());

        assertSame(host, registry.find(ControlActionType.DENY).get());
        assertSame(generic, registry.find(ControlActionType.RATE_LIMIT).get());
        assertFalse(registry.isEmpty());
    }

    @Test
    void anEffectiveGenericTierSatisfiesTheEnforceModeHandlerRequirement() {
        ControlHandler generic = handlerFor(ControlActionType.RATE_LIMIT);
        ControlHandlerRegistry registry = new ControlHandlerRegistry(Collections.<ControlHandler>emptyList(),
            Arrays.asList(generic), DefaultControlActionTrigger.defaults());

        assertFalse(registry.isEmpty());
    }

    @Test
    void twoArgumentConstructorRetainsHostThenDefaultCompatibility() {
        ControlHandler host = handlerFor(ControlActionType.DENY);
        ControlHandler fallback = handlerFor(ControlActionType.RATE_LIMIT);
        ControlHandlerRegistry registry = new ControlHandlerRegistry(Arrays.asList(host), Arrays.asList(fallback));

        assertSame(host, registry.find(ControlActionType.DENY).get());
        assertSame(fallback, registry.find(ControlActionType.RATE_LIMIT).get());
    }

    private static ControlHandler handlerFor(final ControlActionType... supported) {
        return new ControlHandler() {
            @Override
            public boolean supports(ControlActionType action) {
                return Arrays.asList(supported).contains(action);
            }

            @Override
            public ControlExecution execute(ControlCommand command) {
                return ControlExecution.succeeded(command.getIdempotencyKey());
            }
        };
    }

    static final class AnnotatedDeny {
        @ControlTrigger(ControlActionType.DENY)
        public void deny(ControlCommand command) { }
    }

    static final class InvalidAnnotatedDeny {
        @ControlTrigger(ControlActionType.DENY)
        public void deny(String subject) { }
    }
}
