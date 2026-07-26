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
import io.github.jasper.monitoring.api.fact.FactCatalog;
import io.github.jasper.monitoring.api.fact.FactDefinition;
import io.github.jasper.monitoring.api.action.ActionContract;
import io.github.jasper.monitoring.api.action.ActionContractDefinition;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import java.util.Arrays;
import io.github.jasper.monitoring.core.application.DefaultMonitoringRuntime;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultMonitoringRuntimeTest {
    @Test
    void collectsOnlyFactsDeclaredByAnApplicableBinding() {
        ActionCatalog catalog = catalog();
        FactBinding binding = FactBinding.forAction(QueryAction.class, FactSource.HOST_PROVIDER,
            execution -> ActionFacts.builder().put(ResourceFact.class, "report-1").build(), ResourceFact.class);
        DefaultMonitoringRuntime runtime = runtime(catalog, Collections.singletonList(binding));

        ActionFacts facts = runtime.collect(execution(), catalog.require(QueryAction.class)).getFacts();

        assertEquals("report-1", facts.get(ResourceFact.class));
    }

    @Test
    void rejectsUndeclaredProviderOutput() {
        ActionCatalog catalog = catalog();
        FactBinding binding = FactBinding.forAction(QueryAction.class, FactSource.HOST_PROVIDER,
            execution -> ActionFacts.builder().put(UnexpectedFact.class, "unsafe").build(), ResourceFact.class);
        DefaultMonitoringRuntime runtime = runtime(catalog, Collections.singletonList(binding));

        assertThrows(IllegalStateException.class,
            () -> runtime.collect(execution(), catalog.require(QueryAction.class)));
    }

    @Test
    void acceptsSuppliedFactsOnlyFromActionApprovedSource() {
        ActionCatalog catalog = catalog();
        ActionExecution supplied = ActionExecution.of(QueryAction.class, execution().getRequestContext(),
            IdentityContext.anonymous(), ActionOutcome.success(1L),
            ActionFacts.builder().put(ResourceFact.class, "report-2").build(), FactSource.HOST_PROVIDER);

        ActionFacts facts = runtime(catalog, Collections.<FactBinding>emptyList())
            .collect(supplied, catalog.require(QueryAction.class)).getFacts();

        assertEquals("report-2", facts.get(ResourceFact.class));
    }

    @Test
    void rejectsSuppliedFactFromUnapprovedSource() {
        ActionCatalog catalog = catalog();
        ActionExecution supplied = ActionExecution.of(QueryAction.class, execution().getRequestContext(),
            IdentityContext.anonymous(), ActionOutcome.success(1L),
            ActionFacts.builder().put(ResourceFact.class, "report-2").build(), FactSource.CLIENT_SUPPLEMENTAL);

        assertThrows(IllegalStateException.class, () ->
            runtime(catalog, Collections.<FactBinding>emptyList())
                .collect(supplied, catalog.require(QueryAction.class)));
    }

    @Test
    void preservesTheSourceDeclaredByTheProviderBinding() {
        ActionCatalog catalog = new ActionCatalog();
        catalog.register(QueryAction.class, ActionDefinition.builder("data:query")
            .eventType(SecurityEventType.QUERY).resourceType("report")
            .optional(ResourceFact.class, FactSource.TRUSTED_REQUEST)
            .failurePolicy(ActionFailurePolicy.FAIL_CLOSED).build());
        catalog.freeze();
        FactBinding binding = FactBinding.forAction(QueryAction.class, FactSource.TRUSTED_REQUEST,
            execution -> ActionFacts.builder().put(ResourceFact.class, "report-3").build(),
            ResourceFact.class);

        io.github.jasper.monitoring.core.application.MonitoringRuntimePort.FactCollection collected =
            runtime(catalog, Collections.singletonList(binding))
                .collect(execution(), catalog.require(QueryAction.class));

        assertEquals(FactSource.TRUSTED_REQUEST, collected.getSources().get(ResourceFact.class));
    }

    @Test
    void rejectsBindingThatDoesNotMatchARegisteredAction() {
        ActionCatalog catalog = catalog();
        FactBinding binding = FactBinding.forAction(OtherAction.class, FactSource.HOST_PROVIDER,
            execution -> ActionFacts.builder().build(), ResourceFact.class);

        assertThrows(MonitoringConfigurationException.class,
            () -> runtime(catalog, Collections.singletonList(binding)));
    }

    @Test
    void rejectsBindingSourceNotApprovedByTheAction() {
        ActionCatalog catalog = catalog();
        FactBinding binding = FactBinding.forAction(QueryAction.class, FactSource.CLIENT_SUPPLEMENTAL,
            execution -> ActionFacts.builder().build(), ResourceFact.class);

        assertThrows(MonitoringConfigurationException.class,
            () -> runtime(catalog, Collections.singletonList(binding)));
    }

    @Test
    void rejectsOverlappingActionAndContractBindings() {
        ActionCatalog catalog = new ActionCatalog();
        catalog.registerContract(QueryContract.class, ActionContractDefinition.builder()
            .optional(ResourceFact.class, FactSource.HOST_PROVIDER)
            .minimumFailurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build());
        catalog.register(ContractQueryAction.class, ActionDefinition.builder("data:contract-query")
            .eventType(SecurityEventType.QUERY).resourceType("report")
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build());
        catalog.freeze();
        FactBinding contract = FactBinding.forContract(QueryContract.class, FactSource.HOST_PROVIDER,
            execution -> ActionFacts.builder().build(), ResourceFact.class);
        FactBinding exact = FactBinding.forAction(ContractQueryAction.class, FactSource.HOST_PROVIDER,
            execution -> ActionFacts.builder().build(), ResourceFact.class);

        assertThrows(MonitoringConfigurationException.class,
            () -> runtime(catalog, Arrays.asList(contract, exact)));
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

    private static DefaultMonitoringRuntime runtime(ActionCatalog actions, java.util.List<FactBinding> bindings) {
        FactCatalog facts = new FactCatalog();
        facts.register(FactDefinition.builder(ResourceFact.class, "resource", String.class)
            .allowedSources(FactSource.HOST_PROVIDER, FactSource.TRUSTED_REQUEST)
            .sensitivity(FactDefinition.Sensitivity.INTERNAL).maxLength(256)
            .storage(FactDefinition.Storage.EXTENSION)
            .codec(FactDefinition.stringCodec(value -> value.trim())).build());
        facts.freeze();
        return new DefaultMonitoringRuntime(actions, facts, bindings);
    }

    static final class QueryAction implements ActionType { }
    static final class OtherAction implements ActionType { }
    interface QueryContract extends ActionContract { }
    static final class ContractQueryAction implements ActionType, QueryContract { }
    static final class ResourceFact implements FactType<String> { }
    static final class UnexpectedFact implements FactType<String> { }
}
