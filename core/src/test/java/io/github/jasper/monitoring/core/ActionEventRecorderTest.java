package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.core.application.ActionEventRecorder;
import io.github.jasper.monitoring.core.application.SecurityMonitor;
import io.github.jasper.monitoring.core.application.MonitoringActionRegistry;
import io.github.jasper.monitoring.core.application.MonitoringOutcome;
import static org.junit.jupiter.api.Assertions.assertEquals;
import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitorAction;
import io.github.jasper.monitoring.api.MonitorActionDefinition;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ActionEventRecorderTest {

    @Test
    void recordsAnnotationMetadataWithTrustedRequestAndIdentityFacts() throws Exception {
        CapturingMonitor monitor = new CapturingMonitor();
        ActionEventRecorder recorder = new ActionEventRecorder(monitor,
            Clock.fixed(Instant.parse("2026-07-22T01:02:03Z"), ZoneOffset.UTC));
        MonitorAction action = DeclaredAction.class.getMethod("exportReport").getAnnotation(MonitorAction.class);

        recorder.record(action, MonitoringRequestContext.builder()
                .method("POST").path("/reports/export").sourceIp("203.0.113.10")
                .requestId("request-10").traceId("trace-10").build(),
            new IdentityContext("alice", AccountType.PERSON, Collections.singleton("auditor"), "session-hash"),
            SecurityEventResult.SUCCESS, "EXPORT_COMPLETED");

        assertEquals(SecurityEventType.EXPORT, monitor.draft.getEventType());
        assertEquals("EXPORT_REPORT", monitor.draft.getAction());
        assertEquals("report", monitor.draft.getResourceType());
        assertEquals("203.0.113.10", monitor.draft.getSourceIp());
        assertEquals("request-10", monitor.draft.getRequestId());
        assertEquals("trace-10", monitor.draft.getTraceId());
        assertEquals("alice", monitor.draft.getUserId());
        assertEquals(AccountType.PERSON, monitor.draft.getAccountType());
        assertEquals(Collections.singleton("auditor"), monitor.draft.getRoleIds());
        assertEquals("session-hash", monitor.draft.getSessionIdHash());
        assertEquals(SecurityEventResult.SUCCESS, monitor.draft.getResult());
        assertEquals("EXPORT_COMPLETED", monitor.draft.getReasonCode());
        assertEquals(Instant.parse("2026-07-22T01:02:03Z"), monitor.draft.getOccurredAt());
    }

    @Test
    void recordsShorthandAnnotationWithDefaultTypeAndRuleTags() throws Exception {
        CapturingMonitor monitor = new CapturingMonitor();
        ActionEventRecorder recorder = new ActionEventRecorder(monitor,
            Clock.fixed(Instant.parse("2026-07-22T01:02:03Z"), ZoneOffset.UTC));
        MonitorAction action = ShorthandAction.class.getMethod("readReport").getAnnotation(MonitorAction.class);

        recorder.record(action, request(), identity(), SecurityEventResult.SUCCESS, null);

        assertEquals(SecurityEventType.QUERY, monitor.draft.getEventType());
        assertEquals("report:read", monitor.draft.getAction());
        assertEquals("report", monitor.draft.getResourceType());
        assertEquals("true", monitor.draft.getAttribute(
            MonitorActionDefinition.ruleTagAttributeKey("sensitive-data")));
    }

    @Test
    void recordsRegisteredActionAndLetsCallerAddDynamicFacts() {
        CapturingMonitor monitor = new CapturingMonitor();
        MonitoringActionRegistry actions = new MonitoringActionRegistry().register(
            MonitorActionDefinition.builder("report:export")
                .eventType(SecurityEventType.EXPORT)
                .resourceType("report")
                .ruleTag("sensitive-data")
                .build());
        ActionEventRecorder recorder = new ActionEventRecorder(monitor,
            Clock.fixed(Instant.parse("2026-07-22T01:02:03Z"), ZoneOffset.UTC), actions);

        recorder.record(recorder.draft("report:export", request(), identity())
            .result(SecurityEventResult.SUCCESS)
            .resourceId("report-9")
            .dataCount(5000)
            .latencyMs(31)
            .attribute("sensitivity", "HIGH")
            .build());

        assertEquals(SecurityEventType.EXPORT, monitor.draft.getEventType());
        assertEquals("report:export", monitor.draft.getAction());
        assertEquals("report-9", monitor.draft.getResourceId());
        assertEquals(5000L, monitor.draft.getDataCount());
        assertEquals(31L, monitor.draft.getLatencyMs());
        assertEquals("HIGH", monitor.draft.getAttribute("sensitivity"));
        assertEquals("true", monitor.draft.getAttribute(
            MonitorActionDefinition.ruleTagAttributeKey("sensitive-data")));
    }

    @Test
    void draftsStaticActionAttributesBeforeRuleTags() {
        CapturingMonitor monitor = new CapturingMonitor();
        ActionEventRecorder recorder = new ActionEventRecorder(monitor,
            Clock.fixed(Instant.parse("2026-07-22T01:02:03Z"), ZoneOffset.UTC));
        MonitorActionDefinition action = MonitorActionDefinition.builder("login:attempt")
            .attribute("attempted_account_hash", "hash-42")
            .ruleTag("authentication")
            .build();

        recorder.record(recorder.draft(action, request(), identity())
            .result(SecurityEventResult.FAILURE)
            .build());

        assertEquals("hash-42", monitor.draft.getAttribute("attempted_account_hash"));
        assertEquals(Arrays.asList("attempted_account_hash", "monitor.rule-tag.authentication"),
            new ArrayList<String>(monitor.draft.getAttributes().keySet()));
    }

    private static MonitoringRequestContext request() {
        return MonitoringRequestContext.builder()
            .method("POST").path("/reports/export").sourceIp("203.0.113.10")
            .requestId("request-10").traceId("trace-10").build();
    }

    private static IdentityContext identity() {
        return new IdentityContext("alice", AccountType.PERSON, Collections.singleton("auditor"), "session-hash");
    }

    static final class DeclaredAction {
        @MonitorAction(eventType = SecurityEventType.EXPORT, action = "EXPORT_REPORT", resourceType = "report")
        public void exportReport() {
        }
    }

    static final class ShorthandAction {
        @MonitorAction(value = "report:read", resourceType = "report", ruleTags = "sensitive-data")
        public void readReport() {
        }
    }

    private static final class CapturingMonitor implements SecurityMonitor {
        private SecurityEventDraft draft;

        @Override
        public MonitoringOutcome record(SecurityEventDraft value) {
            draft = value;
            return null;
        }
    }
}
