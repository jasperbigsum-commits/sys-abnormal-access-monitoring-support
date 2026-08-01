package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.error.ActionBlockedException;
import io.github.jasper.monitoring.api.event.ActionAttempt;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitoringFactsTest {
    @Test
    void managedAttemptTracksACompletedBlockingDecision() {
        try (MonitoringFactScope scope = MonitoringFactScope.open(TestAction.class,
                facts -> ActionDecision.blocked("EXPT-01"))) {
            MonitoringFacts.put(DataCountFact.class, Long.valueOf(5000L));

            assertThrows(ActionBlockedException.class, MonitoringGate::checkpoint);
            assertEquals(ActionAttempt.Status.DECIDED_BLOCKED, scope.attempt().getStatus());
            scope.complete(ActionOutcome.denied(
                io.github.jasper.monitoring.api.code.BuiltInReasonCodes.Action.BLOCKED, 1L));
            assertEquals(ActionAttempt.Status.COMPLETED_DENIED, scope.attempt().getStatus());
        }
    }

    @Test
    void checkpointFreezesFactsAndReusesTheFirstBlockingDecision() {
        AtomicInteger decisions = new AtomicInteger();
        try (MonitoringFactScope scope = MonitoringFactScope.open(facts -> {
            decisions.incrementAndGet();
            assertEquals(Long.valueOf(5000L), facts.get(DataCountFact.class));
            return ActionDecision.blocked("EXPT-01");
        })) {
            MonitoringFacts.put(DataCountFact.class, Long.valueOf(5000L));

            assertThrows(ActionBlockedException.class, MonitoringGate::checkpoint);
            assertThrows(ActionBlockedException.class, MonitoringGate::checkpoint);
            assertThrows(IllegalStateException.class,
                () -> MonitoringFacts.put(TextFact.class, "late-fact"));
            assertEquals(1, decisions.get());
        }
    }

    @Test
    void checkpointRequiresAManagedActionScope() {
        assertThrows(IllegalStateException.class, MonitoringGate::checkpoint);
    }

    @Test
    void addsFactsToTheActiveScopeAndCleansItAfterClose() {
        try (MonitoringFactScope scope = MonitoringFactScope.open()) {
            assertTrue(MonitoringFacts.put(DataCountFact.class, Long.valueOf(7L)));
            assertEquals(Long.valueOf(7L), scope.snapshot().get(DataCountFact.class));
        }

        assertFalse(MonitoringFacts.put(DataCountFact.class, Long.valueOf(8L)));
    }

    @Test
    void rejectsDuplicateFactsWithoutOverwritingTheFirstValue() {
        try (MonitoringFactScope scope = MonitoringFactScope.open()) {
            assertTrue(MonitoringFacts.put(DataCountFact.class, Long.valueOf(7L)));

            assertThrows(IllegalStateException.class,
                () -> MonitoringFacts.put(DataCountFact.class, Long.valueOf(8L)));
            assertEquals(Long.valueOf(7L), scope.snapshot().get(DataCountFact.class));
        }
    }

    @Test
    void isolatesNestedScopes() {
        try (MonitoringFactScope outer = MonitoringFactScope.open()) {
            MonitoringFacts.put(DataCountFact.class, Long.valueOf(11L));
            try (MonitoringFactScope inner = MonitoringFactScope.open()) {
                MonitoringFacts.put(DataCountFact.class, Long.valueOf(22L));
                assertEquals(Long.valueOf(22L), inner.snapshot().get(DataCountFact.class));
            }
            assertEquals(Long.valueOf(11L), outer.snapshot().get(DataCountFact.class));
        }
    }

    @Test
    void isolatesConcurrentThreads() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<Long> first = executor.submit(() -> scopedValue(31L, ready, release));
            Future<Long> second = executor.submit(() -> scopedValue(47L, ready, release));
            assertTrue(ready.await(5L, TimeUnit.SECONDS));
            release.countDown();

            assertEquals(Long.valueOf(31L), first.get(5L, TimeUnit.SECONDS));
            assertEquals(Long.valueOf(47L), second.get(5L, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsClosingScopesOutOfStackOrder() {
        MonitoringFactScope outer = MonitoringFactScope.open();
        MonitoringFactScope inner = MonitoringFactScope.open();
        try {
            assertThrows(IllegalStateException.class, outer::close);
            inner.close();
            outer.close();
        } finally {
            closeIfOpen(inner);
            closeIfOpen(outer);
        }
    }

    @Test
    void warnsWithoutLoggingTheFactValueWhenNoScopeIsActive() {
        Logger logger = Logger.getLogger(MonitoringFacts.class.getName());
        RecordingHandler handler = new RecordingHandler();
        Level previousLevel = logger.getLevel();
        boolean previousParents = logger.getUseParentHandlers();
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            assertFalse(MonitoringFacts.put(TextFact.class, "sensitive-value"));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previousLevel);
            logger.setUseParentHandlers(previousParents);
        }

        assertEquals(1, handler.messages.size());
        assertTrue(handler.messages.get(0).contains("no active monitored action scope"));
        assertFalse(handler.messages.get(0).contains("sensitive-value"));
    }

    private static Long scopedValue(long value, CountDownLatch ready, CountDownLatch release)
            throws Exception {
        try (MonitoringFactScope scope = MonitoringFactScope.open()) {
            MonitoringFacts.put(DataCountFact.class, Long.valueOf(value));
            ready.countDown();
            assertTrue(release.await(5L, TimeUnit.SECONDS));
            return scope.snapshot().get(DataCountFact.class);
        }
    }

    private static void closeIfOpen(MonitoringFactScope scope) {
        try {
            scope.close();
        } catch (IllegalStateException ignored) {
            // The test cleanup only handles a scope that was already closed.
        }
    }

    static final class DataCountFact implements FactType<Long> { }
    static final class TextFact implements FactType<String> { }
    static final class TestAction implements ActionType { }

    private static final class RecordingHandler extends Handler {
        private final List<String> messages = Collections.synchronizedList(new ArrayList<String>());

        @Override public void publish(LogRecord record) {
            if (record != null) messages.add(record.getMessage());
        }

        @Override public void flush() { }
        @Override public void close() { }
    }
}
