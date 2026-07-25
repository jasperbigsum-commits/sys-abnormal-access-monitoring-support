package io.github.jasper.monitoring.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.ControlStatus;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import io.github.jasper.monitoring.core.application.DefaultControlService;
import io.github.jasper.monitoring.core.application.control.ControlHandlerRegistry;
import io.github.jasper.monitoring.core.application.control.DefaultControlActionTrigger;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.infrastructure.memory.InMemoryMonitoringRepository;
import io.github.jasper.monitoring.core.port.ControlHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DefaultControlServiceTest {

    @Test
    void rejectsRecordAsADefaultControlTriggerWithStableCode() {
        MonitoringValidationException exception = assertThrows(MonitoringValidationException.class,
            () -> DefaultControlActionTrigger.forAction(ControlActionType.RECORD));

        assertEquals(MonitoringErrorCode.INVALID_CONTROL_TRIGGER, exception.getErrorCode());
    }

    @Test
    void retriesADefaultFallbackSkipAfterAHostHandlerBecomesAvailable() {
        AtomicBoolean enabled = new AtomicBoolean();
        AtomicInteger executions = new AtomicInteger();
        ControlHandler host = new ControlHandler() {
            @Override
            public boolean supports(ControlActionType action) {
                return enabled.get() && action == ControlActionType.RATE_LIMIT;
            }

            @Override
            public ControlExecution execute(ControlCommand command) {
                executions.incrementAndGet();
                return ControlExecution.succeeded(command.getIdempotencyKey());
            }
        };
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        DefaultControlService service = new DefaultControlService(repository,
            new ControlHandlerRegistry(Arrays.asList(host), DefaultControlActionTrigger.defaults()),
            Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC));
        ControlCommand command = new ControlCommand("alert-1:RATE_LIMIT", "alert-1", "ip:203.0.113.8",
            ControlActionType.RATE_LIMIT, Instant.parse("2026-07-24T00:30:00Z"));

        ControlExecution skipped = service.execute(command);
        enabled.set(true);
        ControlExecution executed = service.execute(command);
        ControlExecution replayed = service.execute(command);

        assertEquals(ControlStatus.SKIPPED, skipped.getStatus());
        assertEquals(ControlStatus.SUCCEEDED, executed.getStatus());
        assertFalse(executed.isIdempotentReplay());
        assertTrue(replayed.isIdempotentReplay());
        assertEquals(1, executions.get());
        assertEquals(ControlStatus.SUCCEEDED, repository.getControls().get(0).getExecution().getStatus());
    }

    @Test
    void replaysASkipReturnedByTheHostHandlerEvenWhenItsReasonMatchesTheDefaultTrigger() {
        AtomicInteger executions = new AtomicInteger();
        ControlHandler host = new ControlHandler() {
            @Override
            public boolean supports(ControlActionType action) {
                return action == ControlActionType.REQUIRE_APPROVAL;
            }

            @Override
            public ControlExecution execute(ControlCommand command) {
                executions.incrementAndGet();
                return ControlExecution.skipped(command.getIdempotencyKey(),
                    "DEFAULT_TRIGGER_REQUIRES_HOST_HANDLER:REQUIRE_APPROVAL");
            }
        };
        DefaultControlService service = new DefaultControlService(new InMemoryMonitoringRepository(),
            new ControlHandlerRegistry(Arrays.asList(host), DefaultControlActionTrigger.defaults()),
            Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC));
        ControlCommand command = new ControlCommand("default-fallback:host-approval", "alert-1", "user:alice",
            ControlActionType.REQUIRE_APPROVAL, Instant.parse("2026-07-24T00:30:00Z"));

        ControlExecution first = service.execute(command);
        ControlExecution replayed = service.execute(command);

        assertEquals(ControlStatus.SKIPPED, first.getStatus());
        assertTrue(replayed.isIdempotentReplay());
        assertEquals(1, executions.get());
    }
}
