package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.action.ActionCatalog;
import io.github.jasper.monitoring.api.action.ActionDefinition;
import io.github.jasper.monitoring.api.action.ActionFailurePolicy;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactBinding;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.core.application.DefaultMonitoringRuntime;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultMonitoringRuntimeTest {
    @Test
    void collectsOnlyFactsDeclaredByAnApplicableBinding() {
        ActionCatalog catalog = catalog();
        FactBinding binding = FactBinding.forAction(QueryAction.class,
            execution -> ActionFacts.builder().put(ResourceFact.class, "report-1").build(), ResourceFact.class);
        DefaultMonitoringRuntime runtime = new DefaultMonitoringRuntime(catalog, Collections.singletonList(binding));

        ActionFacts facts = runtime.collect(execution(), catalog.require(QueryAction.class));

        assertEquals("report-1", facts.get(ResourceFact.class));
    }

    @Test
    void rejectsUndeclaredProviderOutput() {
        ActionCatalog catalog = catalog();
        FactBinding binding = FactBinding.forAction(QueryAction.class,
            execution -> ActionFacts.builder().put(UnexpectedFact.class, "unsafe").build(), ResourceFact.class);
        DefaultMonitoringRuntime runtime = new DefaultMonitoringRuntime(catalog, Collections.singletonList(binding));

        assertThrows(IllegalStateException.class,
            () -> runtime.collect(execution(), catalog.require(QueryAction.class)));
    }

    private static ActionCatalog catalog() {
        ActionCatalog catalog = new ActionCatalog();
        catalog.register(QueryAction.class, ActionDefinition.builder("data:query")
            .eventType(SecurityEventType.QUERY).resourceType("report")
            .optional(ResourceFact.class, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.FAIL_CLOSED).build());
        catalog.freeze();
        return catalog;
    }

    private static ActionExecution execution() {
        MonitoringRequestContext request = MonitoringRequestContext.builder().method("GET").path("/reports")
            .sourceIp("127.0.0.1").requestId("req-1").build();
        return ActionExecution.of(QueryAction.class, request, IdentityContext.anonymous(), ActionOutcome.success(1L));
    }

    static final class QueryAction implements ActionType { }
    static final class ResourceFact implements FactType<String> { }
    static final class UnexpectedFact implements FactType<String> { }
}
