package io.github.jasper.monitoring.core.application.control;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.control.ControlCatalog;
import io.github.jasper.monitoring.api.control.ControlType;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.domain.control.StoredControl;
import io.github.jasper.monitoring.core.port.ControlExecutionStore;
import io.github.jasper.monitoring.core.port.ControlHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
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
        assertEquals(io.github.jasper.monitoring.api.control.ControlStatus.SUCCEEDED, store.value.status());
    }

    @Test void approvalCanBeApprovedOrRejectedAndFailedExecutionCanRetry() {
        RecordingStore approvedStore = new RecordingStore(); AtomicInteger calls = new AtomicInteger();
        ControlHandler success = handler(command -> { calls.incrementAndGet(); return ControlExecution.succeeded(command.getIdempotencyKey()); });
        ControlCatalog<ControlHandler> approvalCatalog = ControlCatalog.<ControlHandler>builder()
            .bind(ControlType.REQUIRE_APPROVAL, success).freeze();
        ControlExecutionService approvals = service(approvedStore, approvalCatalog);
        ControlCommand approval = new ControlCommand("approval", "a", "s", ControlActionType.REQUIRE_APPROVAL, null, "r");
        assertEquals(io.github.jasper.monitoring.api.control.ControlStatus.AWAITING_APPROVAL, approvals.execute(approval).getStatus());
        assertEquals(0, calls.get());
        assertThrows(IllegalStateException.class, () -> approvals.approve(approval, 9));
        assertEquals(0, calls.get());
        assertEquals(io.github.jasper.monitoring.api.control.ControlStatus.SUCCEEDED, approvals.approve(approval).getStatus());
        assertEquals(1, calls.get());

        RecordingStore rejectedStore = new RecordingStore(); ControlExecutionService rejected = service(rejectedStore, approvalCatalog);
        rejected.execute(new ControlCommand("rejected", "a", "s", ControlActionType.REQUIRE_APPROVAL, null, "r"));
        assertEquals(io.github.jasper.monitoring.api.control.ControlStatus.REJECTED, rejected.reject("rejected", "operator rejected").getStatus());

        RecordingStore retryStore = new RecordingStore(); AtomicInteger attempts = new AtomicInteger();
        ControlHandler flaky = handler(command -> attempts.incrementAndGet() == 1
            ? ControlExecution.failed(command.getIdempotencyKey(), "temporary") : ControlExecution.succeeded(command.getIdempotencyKey()));
        ControlCatalog<ControlHandler> lockCatalog = ControlCatalog.<ControlHandler>builder().bind(ControlType.LOCK, flaky).freeze();
        ControlExecutionService retries = service(retryStore, lockCatalog);
        ControlCommand lock = new ControlCommand("retry", "a", "s", ControlActionType.LOCK, null, "r");
        assertEquals(io.github.jasper.monitoring.api.control.ControlStatus.FAILED, retries.execute(lock).getStatus());
        assertEquals(io.github.jasper.monitoring.api.control.ControlStatus.SUCCEEDED, retries.retry(lock).getStatus());
    }

    private static ControlExecutionService service(RecordingStore store, ControlCatalog<ControlHandler> catalog) {
        return new ControlExecutionService(store, catalog, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }
    private static ControlHandler handler(java.util.function.Function<ControlCommand, ControlExecution> function) {
        return new ControlHandler() {
            public boolean supports(ControlActionType action) { return true; }
            public ControlExecution execute(ControlCommand command) { return function.apply(command); }
        };
    }

    @Test void recoversPendingAfterTerminalWriteFailure() {
        RecordingStore store = new RecordingStore(); store.failTerminalOnce = true;
        ControlHandler handler = handler(command -> ControlExecution.succeeded(command.getIdempotencyKey()));
        ControlExecutionService service = service(store, ControlCatalog.<ControlHandler>builder().bind(ControlType.LOCK, handler).freeze());
        ControlCommand command = new ControlCommand("pending-recovery", "a", "s", ControlActionType.LOCK, null, "r");
        assertThrows(IllegalStateException.class, () -> service.execute(command));
        assertEquals(io.github.jasper.monitoring.api.control.ControlStatus.PENDING, store.value.status());
        assertEquals(io.github.jasper.monitoring.api.control.ControlStatus.SUCCEEDED, service.recover(command).getStatus());
    }

    private static final class RecordingStore implements ControlExecutionStore {
        boolean reserved; boolean failTerminalOnce; StoredControl value;
        public Optional<StoredControl> find(String key) { return Optional.ofNullable(value); }
        public boolean reserve(ControlCommand command, io.github.jasper.monitoring.api.control.ControlStatus status, Instant at) {
            reserved = true; value = new StoredControl(execution(command.getIdempotencyKey(), status, null), 0); return true;
        }
        public StoredControl transition(String key, long version,
                io.github.jasper.monitoring.api.control.ControlStatus expected,
                io.github.jasper.monitoring.api.control.ControlStatus target, String reason, Instant at) {
            if (value.version() != version || value.status() != expected) throw new IllegalStateException("stale");
            if (failTerminalOnce && (target == io.github.jasper.monitoring.api.control.ControlStatus.SUCCEEDED
                || target == io.github.jasper.monitoring.api.control.ControlStatus.FAILED)) { failTerminalOnce = false; throw new IllegalStateException("terminal write failed"); }
            ControlExecution execution = execution(key, target, reason);
            value = new StoredControl(execution, version + 1); return value;
        }
        private static ControlExecution execution(String key, io.github.jasper.monitoring.api.control.ControlStatus status, String reason) {
            switch (status) {
                case PENDING: return ControlExecution.pending(key);
                case AWAITING_APPROVAL: return ControlExecution.awaitingApproval(key);
                case SUCCEEDED: return ControlExecution.succeeded(key);
                case FAILED: return ControlExecution.failed(key, reason);
                case REJECTED: return ControlExecution.rejected(key, reason);
                default: return ControlExecution.skipped(key, reason);
            }
        }
    }
}
