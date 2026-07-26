package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionFailurePolicy;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityEventAssemblerTest {
    @Test
    void writesApprovedBuiltInFactsToStandardEventFields() {
        ActionFacts facts = ActionFacts.builder().put(BuiltInFacts.ResourceId.class, "report-7")
            .put(BuiltInFacts.DataCount.class, 12L).put(BuiltInFacts.Sensitivity.class, "HIGH").build();
        SecurityEventAssembler.AssemblyResult result = new SecurityEventAssembler("demo", fixedClock())
            .assemble(ExportAction.class, ACTION, ActionExecution.of(ExportAction.class, request(),
                IdentityContext.anonymous(), ActionOutcome.success(1L)), facts);

        assertEquals("report-7", result.getEvent().getResourceId());
        assertEquals(12L, result.getEvent().getDataCount());
        assertEquals("HIGH", result.getEvent().getAttribute("sensitivity"));
    }
    private static final ActionDefinition ACTION = ActionDefinition.builder("demo:export")
        .eventType(SecurityEventType.EXPORT).resourceType("report")
        .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();

    @Test
    void frameworkOutcomeWinsOverProviderAndTrustedContextCannotBeReplaced() {
        MonitoringRequestContext request = MonitoringRequestContext.builder().method("GET").path("/reports")
            .sourceIp("10.0.0.1").requestId("req-1").build();
        IdentityContext identity = new IdentityContext("alice", null, Collections.singleton("analyst"), "session-hash");
        ActionExecution execution = ActionExecution.of(ExportAction.class, request, identity, ActionOutcome.denied("server-denied", 17L));
        SecurityEventAssembler.AssemblyResult result = new SecurityEventAssembler("demo", fixedClock())
            .assemble(ExportAction.class, ACTION, execution, ActionFacts.builder().build());
        assertEquals(SecurityEventResult.DENIED, result.getEvent().getResult());
        assertEquals("server-denied", result.getEvent().getReasonCode());
        assertEquals("10.0.0.1", result.getEvent().getSourceIp());
        assertEquals(17L, result.getEvent().getLatencyMs());
    }

    @Test
    void missingRequiredProviderFactProducesObservationIssue() {
        ActionDefinition action = ActionDefinition.builder("demo:export")
            .eventType(SecurityEventType.EXPORT).resourceType("report")
            .require(RequiredFact.class, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
        SecurityEventAssembler.AssemblyResult result = new SecurityEventAssembler("demo", fixedClock())
            .assemble(ExportAction.class, action, ActionExecution.of(ExportAction.class, request(), IdentityContext.anonymous(), ActionOutcome.success(4L)),
                ActionFacts.builder().build());
        assertTrue(result.getIssues().stream().anyMatch(issue -> "MISSING_FACT".equals(issue.getCode())));
    }

    @Test
    void rejectsActionExecutionTypeMismatch() {
        ActionExecution execution = ActionExecution.of(ExportAction.class, request(), IdentityContext.anonymous(), ActionOutcome.success(4L));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            () -> new SecurityEventAssembler("demo", fixedClock()).assemble(OtherAction.class, ACTION, execution,
                ActionFacts.builder().build()));
    }

    private static MonitoringRequestContext request() {
        return MonitoringRequestContext.builder().method("GET").path("/reports").sourceIp("127.0.0.1")
            .requestId("req-1").build();
    }

    private static Clock fixedClock() { return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC); }
    static final class RequiredFact implements FactType<String> { }
    static final class ExportAction implements ActionType { }
    static final class OtherAction implements ActionType { }
}
