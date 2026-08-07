package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.ResourceScopeAuthorizer;
import io.github.jasper.monitoring.api.ResourceScopeRequest;
import io.github.jasper.monitoring.api.ResourceScopeResolution;
import io.github.jasper.monitoring.api.ResourceScopeResolver;
import io.github.jasper.monitoring.api.action.ActionCatalog;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.action.ResourceAccess;
import io.github.jasper.monitoring.api.error.ActionBlockedException;
import io.github.jasper.monitoring.api.fact.ActionFact;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactCatalog;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.core.application.authorization.ResourceAccessGuard;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceAccessStageTest {
    @Test
    void resolvesScopeOnceAndReturnsFactsForTheMonitoringPipeline() throws Exception {
        AtomicInteger resolverCalls = new AtomicInteger();
        AtomicReference<ResourceScopeRequest> authorized = new AtomicReference<ResourceScopeRequest>();
        ResourceScopeResolver resolver = request -> {
            resolverCalls.incrementAndGet();
            assertEquals("report-1", request.getResourceId());
            return ResourceScopeResolution.resolved(ActionFacts.builder()
                .put(BuiltInFacts.OrgScope.class, "org-1").build());
        };
        ResourceAccessStage stage = stage(resolver, (identity, request) -> {
            authorized.set(request);
            return AuthorizationDecision.allowed();
        }, new ArrayList<ActionExecution>());

        ActionFacts resolved = stage.authorize(binding("required", String.class),
            ActionFacts.builder().put(BuiltInFacts.ResourceId.class, "report-1").build());

        assertEquals(1, resolverCalls.get());
        assertEquals("org-1", resolved.get(BuiltInFacts.OrgScope.class));
        assertEquals("org-1", authorized.get().getOrgScope());
    }

    @Test
    void explicitOrgScopeDoesNotReadTheResourceAgain() throws Exception {
        AtomicInteger resolverCalls = new AtomicInteger();
        ResourceAccessStage stage = stage(request -> {
            resolverCalls.incrementAndGet();
            return ResourceScopeResolution.unresolved();
        }, (identity, request) -> AuthorizationDecision.allowed(), new ArrayList<ActionExecution>());

        stage.authorize(binding("required", String.class), ActionFacts.builder()
            .put(BuiltInFacts.ResourceId.class, "report-1")
            .put(BuiltInFacts.OrgScope.class, "org-explicit")
            .build());

        assertEquals(0, resolverCalls.get());
    }

    @Test
    void requiredUnresolvedScopeFailsClosedAndRecordsAccessDenied() throws Exception {
        List<ActionExecution> events = new ArrayList<ActionExecution>();
        AtomicInteger authorizerCalls = new AtomicInteger();
        ResourceAccessStage stage = stage(request -> ResourceScopeResolution.unresolved(),
            (identity, request) -> {
                authorizerCalls.incrementAndGet();
                return AuthorizationDecision.allowed();
            }, events);

        assertThrows(ActionBlockedException.class, () -> stage.authorize(
            binding("required", String.class),
            ActionFacts.builder().put(BuiltInFacts.ResourceId.class, "report-1").build()));

        assertEquals(0, authorizerCalls.get());
        assertEquals(1, events.size());
        assertEquals(BuiltInActions.AccessDenied.class, events.get(0).getActionType());
    }

    @Test
    void resolverFailureFailsClosedBeforeTheHostAuthorizer() throws Exception {
        List<ActionExecution> events = new ArrayList<ActionExecution>();
        AtomicInteger authorizerCalls = new AtomicInteger();
        ResourceAccessStage stage = stage(request -> {
            throw new IllegalStateException("catalog unavailable");
        }, (identity, request) -> {
            authorizerCalls.incrementAndGet();
            return AuthorizationDecision.allowed();
        }, events);

        assertThrows(ActionBlockedException.class, () -> stage.authorize(
            binding("required", String.class),
            ActionFacts.builder().put(BuiltInFacts.ResourceId.class, "report-1").build()));

        assertEquals(0, authorizerCalls.get());
        assertEquals("MON.AUTHZ.EVALUATION_ERROR",
            events.get(0).getOutcome().getReason().getCode());
    }

    @Test
    void rejectsResolverFactsThatDuplicateAnExistingFactType() throws Exception {
        ResourceAccessStage stage = stage(request -> ResourceScopeResolution.resolved(ActionFacts.builder()
                .put(BuiltInFacts.ResourceId.class, "different-report").build()),
            (identity, request) -> AuthorizationDecision.allowed(), new ArrayList<ActionExecution>());

        assertThrows(IllegalStateException.class, () -> stage.authorize(
            binding("optional", String.class),
            ActionFacts.builder().put(BuiltInFacts.ResourceId.class, "report-1").build()));
    }

    @Test
    void rejectsResolverFactsNotDeclaredByTheActionBeforeAuthorization() throws Exception {
        AtomicInteger authorizerCalls = new AtomicInteger();
        ResourceAccessStage stage = stage(request -> ResourceScopeResolution.resolved(ActionFacts.builder()
                .put(BuiltInFacts.DataCount.class, Long.valueOf(9L)).build()),
            (identity, request) -> {
                authorizerCalls.incrementAndGet();
                return AuthorizationDecision.allowed();
            }, new ArrayList<ActionExecution>());

        assertThrows(IllegalStateException.class, () -> stage.authorize(
            binding("optional", String.class),
            ActionFacts.builder().put(BuiltInFacts.ResourceId.class, "report-1").build()));
        assertEquals(0, authorizerCalls.get());
    }

    @Test
    void rejectsInvalidResolverFactValuesBeforeAuthorization() throws Exception {
        AtomicInteger authorizerCalls = new AtomicInteger();
        String invalidOrgScope = String.join("", Collections.nCopies(300, "x"));
        ResourceAccessStage stage = stage(request -> ResourceScopeResolution.resolved(ActionFacts.builder()
                .put(BuiltInFacts.OrgScope.class, invalidOrgScope).build()),
            (identity, request) -> {
                authorizerCalls.incrementAndGet();
                return AuthorizationDecision.allowed();
            }, new ArrayList<ActionExecution>());

        assertThrows(IllegalArgumentException.class, () -> stage.authorize(
            binding("required", String.class),
            ActionFacts.builder().put(BuiltInFacts.ResourceId.class, "report-1").build()));
        assertEquals(0, authorizerCalls.get());
    }

    private static ResourceAccessStage stage(ResourceScopeResolver resolver,
            ResourceScopeAuthorizer authorizer, List<ActionExecution> events) {
        ResourceAccessGuard guard = new ResourceAccessGuard("audit", authorizer, null,
            events::add, Clock.systemUTC());
        return new ResourceAccessStage(guard, context(), resolver,
            new ActionFactExtractor(factCatalog()));
    }

    private static MonitoringContextAccessor context() {
        MonitoringRequestContext request = MonitoringRequestContext.builder()
            .method("GET").path("/reports/report-1").sourceIp("203.0.113.8").requestId("req-1").build();
        IdentityContext identity = new IdentityContext("alice", AccountType.PERSON,
            Collections.singleton("auditor"), null);
        return new MonitoringContextAccessor() {
            @Override public MonitoringRequestContext requestContext() { return request; }
            @Override public IdentityContext identityContext() { return identity; }
        };
    }

    private static MonitorActionContractValidator.MethodBinding binding(
            String method, Class<?>... parameterTypes) throws Exception {
        ActionCatalog actions = new ActionCatalog();
        BuiltInActions.registerInto(actions);
        actions.freeze();
        FactCatalog facts = factCatalog();
        return new MonitorActionContractValidator(actions, facts, Collections.emptyList())
            .validate(Fixture.class.getMethod(method, parameterTypes));
    }

    private static FactCatalog factCatalog() {
        FactCatalog facts = new FactCatalog();
        BuiltInFacts.registerInto(facts);
        facts.freeze();
        return facts;
    }

    public static final class Fixture {
        @MonitorAction(BuiltInActions.Query.class)
        @ResourceAccess(requireOrgScope = true)
        public void required(@ActionFact(BuiltInFacts.ResourceId.class) String resourceId) { }

        @MonitorAction(BuiltInActions.Query.class)
        @ResourceAccess
        public void optional(@ActionFact(BuiltInFacts.ResourceId.class) String resourceId) { }
    }
}
