package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionFailurePolicy;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import io.github.jasper.monitoring.core.infrastructure.memory.InMemoryMonitoringRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitoringServiceTest {
    @Test
    void persistsEventBeforeEvaluatingRules() {
        InMemoryMonitoringRepository repository = new InMemoryMonitoringRepository();
        AtomicBoolean persisted = new AtomicBoolean(false);
        ActionDefinition action = ActionDefinition.builder("demo:query").eventType(SecurityEventType.QUERY)
            .resourceType("report").failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
        MonitoringService service = new MonitoringService(repository,
            new SecurityEventAssembler("demo", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            (event, issues) -> persisted.set(!repository.findEventsSince(Instant.EPOCH).isEmpty()));
        service.monitor(action, ActionExecution.of(request(), IdentityContext.anonymous(), ActionOutcome.success()),
            ActionFacts.builder().build());
        assertTrue(persisted.get());
    }

    private static MonitoringRequestContext request() {
        return MonitoringRequestContext.builder().method("GET").path("/reports").sourceIp("127.0.0.1")
            .requestId("req-1").build();
    }
}
