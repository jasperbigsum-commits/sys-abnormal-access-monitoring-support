package io.github.jasper.monitoring.core.application.control;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.control.ControlCatalog;
import io.github.jasper.monitoring.api.control.ControlType;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.domain.control.ControlAttempt;
import io.github.jasper.monitoring.core.port.ControlExecutionStore;
import io.github.jasper.monitoring.core.port.ControlHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlExecutionServiceTest {
    @Test void reservesBeforeCallingHostHandler() {
        RecordingStore store = new RecordingStore();
        ControlHandler handler = new ControlHandler() {
            public boolean supports(ControlActionType action) { return true; }
            public ControlExecution execute(ControlCommand command) {
                assertTrue(store.reserved); return ControlExecution.succeeded(command.getIdempotencyKey());
            }
        };
        ControlCatalog<ControlHandler> catalog = ControlCatalog.<ControlHandler>builder()
            .bind(ControlType.LOCK, handler).freeze();
        ControlExecutionService service = new ControlExecutionService(store, catalog,
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        ControlExecution result = service.execute(new ControlCommand("k", "a", "s", ControlActionType.LOCK, null, "r"));
        assertEquals(io.github.jasper.monitoring.api.ControlStatus.SUCCEEDED, result.getStatus());
        assertEquals(2, store.attempts.size());
    }

    private static final class RecordingStore implements ControlExecutionStore {
        boolean reserved; final List<ControlAttempt> attempts = new ArrayList<ControlAttempt>();
        public Optional<ControlExecution> find(String key) { return Optional.empty(); }
        public boolean reserve(ControlCommand command) { reserved = true; return true; }
        public void appendAttempt(ControlAttempt attempt) { attempts.add(attempt); }
        public void complete(String key, ControlExecution execution) { }
    }
}
