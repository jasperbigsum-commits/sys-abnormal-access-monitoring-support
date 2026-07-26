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
    private static final ActionDefinition ACTION = ActionDefinition.builder("demo:export")
        .eventType(SecurityEventType.EXPORT).resourceType("report")
        .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();

    @Test
    void frameworkOutcomeWinsOverProviderAndTrustedContextCannotBeReplaced() {
        MonitoringRequestContext request = MonitoringRequestContext.builder().method("GET").path("/reports")
            .sourceIp("10.0.0.1").requestId("req-1").build();
        IdentityContext identity = new IdentityContext("alice", null, Collections.singleton("analyst"), "session-hash");
        ActionExecution execution = ActionExecution.of(ExportAction.class, request, identity, ActionOutcome.denied("server-denied"));
        SecurityEventAssembler.AssemblyResult result = new SecurityEventAssembler("demo", fixedClock())
            .assemble(ACTION, execution, ActionFacts.builder().build());
        assertEquals(SecurityEventResult.DENIED, result.getEvent().getResult());
        assertEquals("server-denied", result.getEvent().getReasonCode());
        assertEquals("10.0.0.1", result.getEvent().getSourceIp());
    }

    @Test
    void missingRequiredProviderFactProducesObservationIssue() {
        ActionDefinition action = ActionDefinition.builder("demo:export")
            .eventType(SecurityEventType.EXPORT).resourceType("report")
            .require(RequiredFact.class, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
        SecurityEventAssembler.AssemblyResult result = new SecurityEventAssembler("demo", fixedClock())
            .assemble(action, ActionExecution.of(ExportAction.class, request(), IdentityContext.anonymous(), ActionOutcome.success()),
                ActionFacts.builder().build());
        assertTrue(result.getIssues().stream().anyMatch(issue -> "MISSING_FACT".equals(issue.getCode())));
    }

    private static MonitoringRequestContext request() {
        return MonitoringRequestContext.builder().method("GET").path("/reports").sourceIp("127.0.0.1")
            .requestId("req-1").build();
    }

    private static Clock fixedClock() { return Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC); }
    static final class RequiredFact implements FactType<String> { }
    static final class ExportAction implements ActionType { }
}
