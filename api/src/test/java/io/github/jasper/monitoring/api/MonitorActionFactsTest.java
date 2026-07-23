package io.github.jasper.monitoring.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class MonitorActionFactsTest {

    @Test
    void keepsOnlyAllowedSanitizedDynamicFacts() throws Exception {
        MonitorActionInvocation invocation = MonitorActionInvocation.before(
            definition(), Sample.class.getMethod("export", String.class), new Object[] { "report-1" });

        MonitorActionFacts facts = MonitorActionFacts.builder()
            .resourceId("report-1\n")
            .orgScope("org-a\r\n")
            .dataCount(5)
            .latencyMs(19)
            .result(SecurityEventResult.SUCCESS)
            .reasonCode("EXPORT_COMPLETED\n")
            .attribute("sensitivity", "HIGH\n")
            .build();

        assertEquals("report-1", facts.getResourceId());
        assertEquals("org-a", facts.getOrgScope());
        assertEquals(5L, facts.getDataCount().getAsLong());
        assertEquals(19L, facts.getLatencyMs().getAsLong());
        assertEquals(SecurityEventResult.SUCCESS, facts.getResult());
        assertEquals("EXPORT_COMPLETED", facts.getReasonCode());
        assertEquals("HIGH", facts.getAttributes().get("sensitivity"));
        assertEquals(MonitorActionInvocation.Phase.BEFORE, invocation.getPhase());
    }

    @Test
    void rejectsForbiddenDynamicFactAttributes() {
        assertThrows(IllegalArgumentException.class, () -> MonitorActionFacts.builder()
            .attribute("password", "not-allowed").build());
    }

    @Test
    void exposesOnlyDefensiveArgumentsDuringReturningInvocation() throws Exception {
        Object[] arguments = { "report-1" };
        Method method = Sample.class.getMethod("export", String.class);
        MonitorActionInvocation invocation = MonitorActionInvocation.returning(
            definition(), method, arguments, "completed", 12L);
        arguments[0] = "changed";

        assertEquals(MonitorActionInvocation.Phase.AFTER_RETURNING, invocation.getPhase());
        assertArrayEquals(new Object[] { "report-1" }, invocation.getArguments());
        invocation.getArguments()[0] = "changed-again";
        assertArrayEquals(new Object[] { "report-1" }, invocation.getArguments());
        assertEquals("completed", invocation.getReturnValue());
        assertNull(invocation.getFailure());
        assertEquals(12L, invocation.getElapsedMs());
    }

    @Test
    void capturesThrowingPhaseAndRejectsNegativeElapsedTime() throws Exception {
        IllegalStateException failure = new IllegalStateException("failed");
        MonitorActionInvocation invocation = MonitorActionInvocation.throwing(
            definition(), Sample.class.getMethod("export", String.class), new Object[] { "report-1" }, failure, 3L);

        assertEquals(MonitorActionInvocation.Phase.AFTER_THROWING, invocation.getPhase());
        assertSame(failure, invocation.getFailure());
        assertNull(invocation.getReturnValue());
        assertThrows(IllegalArgumentException.class, () -> MonitorActionInvocation.returning(
            definition(), Sample.class.getMethod("export", String.class), new Object[0], "completed", -1L));
    }

    @Test
    void exposesEmptyOptionalCountsForEmptyFacts() {
        MonitorActionFacts facts = MonitorActionFacts.empty();

        assertNull(facts.getResourceId());
        assertNull(facts.getOrgScope());
        assertFalse(facts.getDataCount().isPresent());
        assertFalse(facts.getLatencyMs().isPresent());
        assertNull(facts.getResult());
        assertNull(facts.getReasonCode());
        assertTrue(facts.getAttributes().isEmpty());
    }

    private static MonitorActionDefinition definition() {
        return MonitorActionDefinition.builder("report:export").build();
    }

    private static final class Sample {
        public String export(String reportId) {
            return reportId;
        }
    }
}
