package io.github.jasper.monitoring.api;

/**
 * 传递给宿主 {@link ResourceScopeAuthorizer} 的不可变资源事实。
 *
 * <p>请求上下文用于标识操作，资源字段用于标识待授权目标；所有可选资源字段都会经过清洗。</p>
 */
public final class ResourceScopeRequest {
    private final MonitoringRequestContext request;
    private final String resourceType;
    private final String resourceId;
    private final String orgScope;
    /**
     * 创建资源范围授权请求。
     *
     * @param request 可信请求上下文
     * @param resourceType 逻辑资源类别，例如 {@code REPORT}
     * @param resourceId 适用时提供的宿主资源标识
     * @param orgScope 适用时提供的租户、组织或数据域边界
     */
    public ResourceScopeRequest(MonitoringRequestContext request, String resourceType, String resourceId, String orgScope) {
        this.request = request;
        this.resourceType = SecurityFieldSanitizer.text(resourceType, 128);
        this.resourceId = SecurityFieldSanitizer.text(resourceId, 256);
        this.orgScope = SecurityFieldSanitizer.text(orgScope, 256);
    }
    /** @return 正在授权的操作对应的请求上下文 */
    public MonitoringRequestContext getRequest() { return request; }
    /** @return 已清洗的资源类别；不可用时为 {@code null} */
    public String getResourceType() { return resourceType; }
    /** @return 已清洗的资源标识；不可用时为 {@code null} */
    public String getResourceId() { return resourceId; }
    /** @return 已清洗的组织或数据域边界；不可用时为 {@code null} */
    public String getOrgScope() { return orgScope; }
}
