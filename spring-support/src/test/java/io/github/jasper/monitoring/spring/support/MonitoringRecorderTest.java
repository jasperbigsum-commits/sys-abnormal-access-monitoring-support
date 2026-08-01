package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionFailurePolicy;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.api.code.StableCodeCatalog;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.core.application.MonitoringRuntimePort;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import io.github.jasper.monitoring.core.domain.EventFact;
import io.github.jasper.monitoring.core.domain.SecurityEvent;
import io.github.jasper.monitoring.core.port.EventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class MonitoringRecorderTest {

    @Test
    void recordsHostFactsWithTheCurrentTrustedRequestAndIdentity() {
        MonitoringRequestContext request = MonitoringRequestContext.builder().method("POST")
            .path("/reports/report-9/export").sourceIp("127.0.0.1").requestId("request-9").build();
        IdentityContext identity = new IdentityContext("user-9", AccountType.PERSON,
            Collections.singleton("report-exporter"), "session-hash");
        RecordingRepository repository = new RecordingRepository();
        CapturingRuntime runtime = new CapturingRuntime();
        MonitoringService service = new MonitoringService(repository,
            new SecurityEventAssembler("test-system", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            runtime, (type, definition, event, facts, sources, ineligible, issues) -> { }, stableCodes());
        MonitoringContextAccessor contexts = new MonitoringContextAccessor() {
            @Override public MonitoringRequestContext requestContext() { return request; }
            @Override public IdentityContext identityContext() { return identity; }
        };
        MonitoringRecorder recorder = new MonitoringRecorder(service, contexts);
        ActionFacts facts = ActionFacts.builder().put(ResourceFact.class, "report-9").build();

        SecurityEventAssembler.AssemblyResult result = recorder.record(QueryAction.class,
            ActionOutcome.success(4L), facts);

        assertSame(request, runtime.execution.getRequestContext());
        assertSame(identity, runtime.execution.getIdentityContext());
        assertEquals(FactSource.HOST_PROVIDER,
            runtime.execution.getSuppliedFactSources().get(ResourceFact.class));
        assertSame(result.getEvent(), repository.events.get(0));
        assertEquals("user-9", result.getEvent().getUserId());
        assertEquals("request-9", result.getEvent().getRequestId());
    }

    static final class QueryAction implements ActionType { }
    static final class ResourceFact implements FactType<String> { }

    private static StableCodeCatalog stableCodes() {
        StableCodeCatalog catalog = new StableCodeCatalog("");
        BuiltInReasonCodes.registerInto(catalog);
        catalog.freeze();
        return catalog;
    }

    private static final class CapturingRuntime implements MonitoringRuntimePort {
        private final ActionDefinition action = ActionDefinition.builder("data:test-query")
            .eventType(SecurityEventType.QUERY).resourceType("report")
            .optional(ResourceFact.class, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
        private ActionExecution execution;

        @Override public ActionDefinition resolve(Class<? extends ActionType> actionType) {
            return action;
        }

        @Override public FactCollection collect(ActionExecution execution, ActionDefinition definition) {
            this.execution = execution;
            Map<Class<? extends FactType<?>>, FactSource> sources =
                new LinkedHashMap<Class<? extends FactType<?>>, FactSource>(
                    execution.getSuppliedFactSources());
            List<EventFact> persisted = Collections.singletonList(new EventFact("resource",
                String.class.getName(), execution.getSuppliedFacts().get(ResourceFact.class),
                sources.get(ResourceFact.class)));
            return new FactCollection(execution.getSuppliedFacts(), sources, persisted);
        }
    }

    private static final class RecordingRepository implements EventRepository {
        private final List<SecurityEvent> events = new ArrayList<SecurityEvent>();

        @Override public void save(SecurityEvent event) { events.add(event); }
        @Override public Optional<SecurityEvent> findEvent(String eventId) { return Optional.empty(); }
        @Override public List<SecurityEvent> findSince(String systemId, Instant since) {
            return new ArrayList<SecurityEvent>(events);
        }
    }
}
