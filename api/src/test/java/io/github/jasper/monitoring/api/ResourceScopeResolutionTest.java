package io.github.jasper.monitoring.api;

import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import java.util.Collections;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceScopeResolutionTest {
    @Test
    void carriesOnlyTrustedServerContextAndTypedFacts() {
        MonitoringRequestContext request = MonitoringRequestContext.builder()
            .method("GET").path("/reports/r-1").sourceIp("203.0.113.8").requestId("req-1").build();
        IdentityContext identity = new IdentityContext("alice", AccountType.PERSON,
            Collections.singleton("auditor"), null);
        ActionFacts facts = ActionFacts.builder()
            .put(BuiltInFacts.ResourceId.class, "r-1")
            .build();

        ResourceScopeResolveRequest resolutionRequest = new ResourceScopeResolveRequest(
            request, identity, BuiltInActions.ReportExport.class, "report:export", "report", "r-1", facts);

        assertSame(request, resolutionRequest.getRequest());
        assertSame(identity, resolutionRequest.getIdentity());
        assertEquals(BuiltInActions.ReportExport.class, resolutionRequest.getActionType());
        assertEquals("report:export", resolutionRequest.getActionCode());
        assertEquals("report", resolutionRequest.getResourceType());
        assertEquals("r-1", resolutionRequest.getResourceId());
        assertSame(facts, resolutionRequest.getFacts());
    }

    @Test
    void distinguishesUnresolvedFromResolvedTypedFacts() {
        ResourceScopeResolution unresolved = ResourceScopeResolution.unresolved();
        ResourceScopeResolution resolved = ResourceScopeResolution.resolved(ActionFacts.builder()
            .put(BuiltInFacts.OrgScope.class, "org-1")
            .build());

        assertFalse(unresolved.isResolved());
        assertTrue(unresolved.getFacts().asMap().isEmpty());
        assertTrue(resolved.isResolved());
        assertEquals("org-1", resolved.getFacts().get(BuiltInFacts.OrgScope.class));
    }

    @Test
    void rejectsNullResolverInputs() {
        assertThrows(NullPointerException.class, () -> ResourceScopeResolution.resolved(null));
        assertThrows(NullPointerException.class, () -> new ResourceScopeResolveRequest(
            null, IdentityContext.anonymous(), BuiltInActions.Query.class,
            "data:query", "resource", "r-1", ActionFacts.builder().build()));
    }

    @Test
    void resourceAuthorizationRequestCarriesTheResolvedTypedSnapshot() {
        ActionFacts facts = ActionFacts.builder()
            .put(BuiltInFacts.ResourceId.class, "r-1")
            .put(BuiltInFacts.OrgScope.class, "org-1")
            .build();
        MonitoringRequestContext request = MonitoringRequestContext.builder()
            .method("GET").path("/reports/r-1").sourceIp("203.0.113.8").requestId("req-2").build();

        ResourceScopeRequest authorization = new ResourceScopeRequest(request, "report", "r-1",
            "org-1", null, null, true, false, facts);

        assertSame(facts, authorization.getFacts());
    }
}
