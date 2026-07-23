package io.github.jasper.monitoring.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class HostIntegrationContractsTest {

    @Test
    void keepsAuthorizationDecisionExplicitAndServerOwned() {
        MonitoringRequestContext request = MonitoringRequestContext.builder()
            .method("GET")
            .path("/admin/users")
            .sourceIp("203.0.113.12")
            .requestId("req-1")
            .build();
        ResourceScopeRequest scope = new ResourceScopeRequest(request, "user", "u-2", "org-2");
        AuthorizationDecision decision = AuthorizationDecision.denied("RESOURCE_SCOPE_DENIED");

        assertFalse(decision.isAllowed());
        assertTrue(scope.getRequest().getPath().startsWith("/admin"));
    }
}
