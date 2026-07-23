package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.application.control.AnnotatedControlHandler;
import io.github.jasper.monitoring.core.port.ControlHandler;
import io.github.jasper.monitoring.core.application.control.ControlHandlerRegistry;
import io.github.jasper.monitoring.core.infrastructure.memory.InMemoryMonitoringRepository;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.port.NotificationChannel;
import io.github.jasper.monitoring.core.application.DefaultSecurityMonitor;
import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.ControlTrigger;
import io.github.jasper.monitoring.api.MonitoringMode;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AnnotatedControlHandlerTest {

    @Test
    void invokesAnnotatedVoidMethodAndReportsSuccess() {
        VoidTarget target = new VoidTarget();
        AnnotatedControlHandler handler = new AnnotatedControlHandler(target);
        ControlCommand command = command(ControlActionType.LOCK);

        ControlExecution execution = handler.execute(command);

        assertTrue(AnnotatedControlHandler.hasBindings(target));
        assertTrue(handler.supports(ControlActionType.LOCK));
        assertFalse(handler.supports(ControlActionType.DENY));
        assertSame(command, target.command);
        assertTrue(execution.isSucceeded());
    }

    @Test
    void invokesPublicBindingOnPackageVisibleHostType() {
        PackageVisibleTarget target = new PackageVisibleTarget();
        AnnotatedControlHandler handler = new AnnotatedControlHandler(target);

        ControlExecution execution = handler.execute(command(ControlActionType.RATE_LIMIT));

        assertTrue(execution.isSucceeded());
        assertTrue(target.invoked);
    }

    @Test
    void defersLazyTargetConstructionUntilControlExecution() {
        AtomicInteger creations = new AtomicInteger();
        AtomicReference<LazyTarget> target = new AtomicReference<LazyTarget>();
        ControlHandler handler = AnnotatedControlHandler.lazy(LazyTarget.class, () -> {
            LazyTarget value = new LazyTarget();
            target.set(value);
            creations.incrementAndGet();
            return value;
        });

        assertTrue(handler.supports(ControlActionType.REQUIRE_APPROVAL));
        assertEquals(0, creations.get());

        ControlExecution execution = handler.execute(command(ControlActionType.REQUIRE_APPROVAL));
        ControlExecution repeatedExecution = handler.execute(command(ControlActionType.REQUIRE_APPROVAL));

        assertTrue(execution.isSucceeded());
        assertTrue(repeatedExecution.isSucceeded());
        assertEquals(2, creations.get());
        assertTrue(target.get().invoked);
    }

    @Test
    void invokesAControlMethodThroughAJdkProxy() {
        ProxyTarget target = new ProxyTarget();
        ProxyControl proxy = (ProxyControl) Proxy.newProxyInstance(ProxyControl.class.getClassLoader(),
            new Class<?>[] {ProxyControl.class}, (ignored, method, arguments) -> {
                target.invoked = "deny".equals(method.getName());
                return null;
            });
        ControlHandler handler = AnnotatedControlHandler.lazy(ProxyTarget.class, () -> proxy);

        ControlExecution execution = handler.execute(command(ControlActionType.DENY));

        assertTrue(execution.isSucceeded());
        assertTrue(target.invoked);
    }

    @Test
    void returnsControlExecutionFromAnnotatedMethod() {
        AnnotatedControlHandler handler = new AnnotatedControlHandler(new ReturningTarget());

        ControlExecution execution = handler.execute(command(ControlActionType.DENY));

        assertEquals("already denied", execution.getFailureReason());
    }

    @Test
    void rejectsDuplicateAndInvalidBindings() {
        assertThrows(IllegalArgumentException.class, () -> new AnnotatedControlHandler(new DuplicateTarget()));
        assertThrows(IllegalArgumentException.class, () -> new AnnotatedControlHandler(new InvalidTarget()));
        assertThrows(IllegalArgumentException.class, () -> new AnnotatedControlHandler(new RecordTarget()));
    }

    @Test
    void turnsAnnotatedMethodFailuresIntoSafeControlFailures() {
        AnnotatedControlHandler handler = new AnnotatedControlHandler(new FailingTarget());

        ControlExecution execution = handler.execute(command(ControlActionType.REQUIRE_MFA));

        assertFalse(execution.isSucceeded());
        assertEquals("Annotated control method failed", execution.getFailureReason());
    }

    @Test
    void emptyAnnotatedHandlerCannotSatisfyEnforceMode() {
        ControlHandlerRegistry handlers = new ControlHandlerRegistry(
            Arrays.<ControlHandler>asList(new AnnotatedControlHandler(new EmptyTarget())));

        assertTrue(handlers.isEmpty());
        assertThrows(IllegalStateException.class, () -> new DefaultSecurityMonitor(
            "orders", Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC),
            new InMemoryMonitoringRepository(), Collections.<DetectionRule>emptyList(), MonitoringMode.ENFORCE,
            handlers, NotificationChannel.noop()));
    }

    private static ControlCommand command(ControlActionType action) {
        return new ControlCommand("key-1", "alert-1", "alice", action, Instant.parse("2026-07-22T01:00:00Z"));
    }

    public static final class VoidTarget {
        private ControlCommand command;

        @ControlTrigger(ControlActionType.LOCK)
        public void lock(ControlCommand value) {
            command = value;
        }
    }

    public static final class ReturningTarget {
        @ControlTrigger(ControlActionType.DENY)
        public ControlExecution deny(ControlCommand value) {
            return ControlExecution.skipped(value.getIdempotencyKey(), "already denied");
        }
    }

    static final class PackageVisibleTarget {
        private boolean invoked;

        @ControlTrigger(ControlActionType.RATE_LIMIT)
        public void rateLimit(ControlCommand value) {
            invoked = true;
        }
    }

    static final class LazyTarget {
        private boolean invoked;

        @ControlTrigger(ControlActionType.REQUIRE_APPROVAL)
        public void requireApproval(ControlCommand value) {
            invoked = true;
        }
    }

    interface ProxyControl {
        void deny(ControlCommand value);
    }

    static final class ProxyTarget implements ProxyControl {
        private boolean invoked;

        @Override
        @ControlTrigger(ControlActionType.DENY)
        public void deny(ControlCommand value) {
            invoked = true;
        }
    }

    public static final class DuplicateTarget {
        @ControlTrigger(ControlActionType.LOCK)
        public void lock(ControlCommand value) {
        }

        @ControlTrigger(ControlActionType.LOCK)
        public void lockAgain(ControlCommand value) {
        }
    }

    public static final class InvalidTarget {
        @ControlTrigger(ControlActionType.LOCK)
        public String invalid(ControlCommand value) {
            return "invalid";
        }
    }

    public static final class RecordTarget {
        @ControlTrigger(ControlActionType.RECORD)
        public void record(ControlCommand value) {
        }
    }

    public static final class FailingTarget {
        @ControlTrigger(ControlActionType.REQUIRE_MFA)
        public void requireMfa(ControlCommand value) {
            throw new IllegalStateException("host failure");
        }
    }

    public static final class EmptyTarget {
    }
}
