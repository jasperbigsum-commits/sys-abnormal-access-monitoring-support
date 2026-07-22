package io.github.jasper.monitoring.api;

/**
 * Immutable resource facts passed to a host {@link ResourceScopeAuthorizer}.
 *
 * <p>The request context identifies the operation; resource fields identify the target being
 * authorized. All optional resource fields are sanitized.</p>
 */
public final class ResourceScopeRequest {
    private final MonitoringRequestContext request;
    private final String resourceType;
    private final String resourceId;
    private final String orgScope;
    /**
     * Creates a resource-scope authorization request.
     *
     * @param request trusted request context
     * @param resourceType logical resource category, such as {@code REPORT}
     * @param resourceId host resource identifier, when applicable
     * @param orgScope tenant, organization, or data-domain boundary, when applicable
     */
    public ResourceScopeRequest(MonitoringRequestContext request, String resourceType, String resourceId, String orgScope) {
        this.request = request;
        this.resourceType = SecurityFieldSanitizer.text(resourceType, 128);
        this.resourceId = SecurityFieldSanitizer.text(resourceId, 256);
        this.orgScope = SecurityFieldSanitizer.text(orgScope, 256);
    }
    /** @return context for the operation being authorized */
    public MonitoringRequestContext getRequest() { return request; }
    /** @return sanitized resource category, or {@code null} when unavailable */
    public String getResourceType() { return resourceType; }
    /** @return sanitized resource identifier, or {@code null} when unavailable */
    public String getResourceId() { return resourceId; }
    /** @return sanitized organization or data-domain boundary, or {@code null} when unavailable */
    public String getOrgScope() { return orgScope; }
}
