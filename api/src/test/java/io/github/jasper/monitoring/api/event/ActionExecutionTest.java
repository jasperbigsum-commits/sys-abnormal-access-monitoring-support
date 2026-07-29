package io.github.jasper.monitoring.api.event;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActionExecutionTest {

    @Test
    void preservesTheSourceOfEachSuppliedFact() {
        ActionFacts facts = facts();
        Map<Class<? extends FactType<?>>, FactSource> sources =
            new LinkedHashMap<Class<? extends FactType<?>>, FactSource>();
        sources.put(ResourceFact.class, FactSource.METHOD_PARAMETER);
        sources.put(DataCountFact.class, FactSource.HOST_PROVIDER);

        ActionExecution execution = ActionExecution.of(QueryAction.class, request(),
            IdentityContext.anonymous(), ActionOutcome.success(3L), facts, sources);

        assertEquals(FactSource.METHOD_PARAMETER,
            execution.getSuppliedFactSources().get(ResourceFact.class));
        assertEquals(FactSource.HOST_PROVIDER,
            execution.getSuppliedFactSources().get(DataCountFact.class));
        assertThrows(UnsupportedOperationException.class,
            () -> execution.getSuppliedFactSources().clear());
    }

    @Test
    void expandsTheLegacyUniformSourceAcrossAllFacts() {
        ActionExecution execution = ActionExecution.of(QueryAction.class, request(),
            IdentityContext.anonymous(), ActionOutcome.success(3L), facts(),
            FactSource.HOST_PROVIDER);

        assertEquals(FactSource.HOST_PROVIDER, execution.getSuppliedFactSource());
        assertEquals(FactSource.HOST_PROVIDER,
            execution.getSuppliedFactSources().get(ResourceFact.class));
        assertEquals(FactSource.HOST_PROVIDER,
            execution.getSuppliedFactSources().get(DataCountFact.class));
    }

    @Test
    void rejectsMissingAndExtraSourceEntries() {
        Map<Class<? extends FactType<?>>, FactSource> missing =
            new LinkedHashMap<Class<? extends FactType<?>>, FactSource>();
        missing.put(ResourceFact.class, FactSource.METHOD_PARAMETER);
        Map<Class<? extends FactType<?>>, FactSource> extra =
            new LinkedHashMap<Class<? extends FactType<?>>, FactSource>();
        extra.put(ResourceFact.class, FactSource.METHOD_PARAMETER);
        extra.put(DataCountFact.class, FactSource.HOST_PROVIDER);
        extra.put(ExtraFact.class, FactSource.HOST_PROVIDER);

        assertThrows(IllegalArgumentException.class, () -> ActionExecution.of(QueryAction.class,
            request(), IdentityContext.anonymous(), ActionOutcome.success(3L), facts(), missing));
        assertThrows(IllegalArgumentException.class, () -> ActionExecution.of(QueryAction.class,
            request(), IdentityContext.anonymous(), ActionOutcome.success(3L), facts(), extra));
    }

    @Test
    void rejectsNullFactSources() {
        Map<Class<? extends FactType<?>>, FactSource> sources =
            new LinkedHashMap<Class<? extends FactType<?>>, FactSource>();
        sources.put(ResourceFact.class, FactSource.METHOD_PARAMETER);
        sources.put(DataCountFact.class, null);

        assertThrows(NullPointerException.class, () -> ActionExecution.of(QueryAction.class,
            request(), IdentityContext.anonymous(), ActionOutcome.success(3L), facts(), sources));
    }

    private static ActionFacts facts() {
        return ActionFacts.builder()
            .put(ResourceFact.class, "report-1")
            .put(DataCountFact.class, Long.valueOf(7L))
            .build();
    }

    private static MonitoringRequestContext request() {
        return MonitoringRequestContext.builder().method("GET").path("/reports/report-1")
            .sourceIp("127.0.0.1").requestId("request-1").build();
    }

    static final class QueryAction implements ActionType { }
    static final class ResourceFact implements FactType<String> { }
    static final class DataCountFact implements FactType<Long> { }
    static final class ExtraFact implements FactType<String> { }
}
