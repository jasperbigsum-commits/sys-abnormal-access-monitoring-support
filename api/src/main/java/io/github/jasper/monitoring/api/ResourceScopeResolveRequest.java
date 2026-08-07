package io.github.jasper.monitoring.api;

import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import java.util.Objects;

/** Immutable trusted context supplied to a host {@link ResourceScopeResolver}. */
public final class ResourceScopeResolveRequest {
    private final MonitoringRequestContext request;
    private final IdentityContext identity;
    private final Class<? extends ActionType> actionType;
    private final String actionCode;
    private final String resourceType;
    private final String resourceId;
    private final ActionFacts facts;

    public ResourceScopeResolveRequest(MonitoringRequestContext request, IdentityContext identity,
            Class<? extends ActionType> actionType, String actionCode, String resourceType,
            String resourceId, ActionFacts facts) {
        this.request = Objects.requireNonNull(request, "request");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.actionType = Objects.requireNonNull(actionType, "actionType");
        this.actionCode = SecurityFieldSanitizer.text(actionCode, 128);
        this.resourceType = SecurityFieldSanitizer.text(resourceType, 128);
        this.resourceId = SecurityFieldSanitizer.text(resourceId, 256);
        this.facts = Objects.requireNonNull(facts, "facts");
    }

    public MonitoringRequestContext getRequest() { return request; }
    public IdentityContext getIdentity() { return identity; }
    public Class<? extends ActionType> getActionType() { return actionType; }
    public String getActionCode() { return actionCode; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public ActionFacts getFacts() { return facts; }
}
