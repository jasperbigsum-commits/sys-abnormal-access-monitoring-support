package io.github.jasper.monitoring.api;

import io.github.jasper.monitoring.api.fact.ActionFacts;
import java.util.Objects;

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
    private final String passRuleId;
    private final String passSubject;
    private final boolean orgScopeRequired;
    private final boolean scopeResolutionFailed;
    private final ActionFacts facts;
    /**
     * 创建资源范围授权请求。
     *
     * @param request 可信请求上下文
     * @param resourceType 逻辑资源类别，例如 {@code REPORT}
     * @param resourceId 适用时提供的宿主资源标识
     * @param orgScope 适用时提供的租户、组织或数据域边界
     */
    public ResourceScopeRequest(MonitoringRequestContext request, String resourceType, String resourceId, String orgScope) {
        this(request, resourceType, resourceId, orgScope, null, null, false, false);
    }

    /**
     * 创建可由服务端通行证覆盖监测阻断的资源授权请求。
     * 规则和主体必须来自可信服务端上下文，不得直接采用客户端参数。
     */
    public ResourceScopeRequest(MonitoringRequestContext request, String resourceType, String resourceId,
            String orgScope, String passRuleId, String passSubject) {
        this(request, resourceType, resourceId, orgScope, passRuleId, passSubject, false, false);
    }

    /** Creates a request carrying the resource-resolution state used by the guard. */
    public ResourceScopeRequest(MonitoringRequestContext request, String resourceType, String resourceId,
            String orgScope, String passRuleId, String passSubject,
            boolean orgScopeRequired, boolean scopeResolutionFailed) {
        this(request, resourceType, resourceId, orgScope, passRuleId, passSubject,
            orgScopeRequired, scopeResolutionFailed, ActionFacts.builder().build());
    }

    /** Creates a request with the complete typed resource fact snapshot. */
    public ResourceScopeRequest(MonitoringRequestContext request, String resourceType, String resourceId,
            String orgScope, String passRuleId, String passSubject,
            boolean orgScopeRequired, boolean scopeResolutionFailed, ActionFacts facts) {
        this.request = request;
        this.resourceType = SecurityFieldSanitizer.text(resourceType, 128);
        this.resourceId = SecurityFieldSanitizer.text(resourceId, 256);
        this.orgScope = SecurityFieldSanitizer.text(orgScope, 256);
        this.passRuleId = SecurityFieldSanitizer.text(passRuleId, 128);
        this.passSubject = SecurityFieldSanitizer.text(passSubject, 512);
        this.orgScopeRequired = orgScopeRequired;
        this.scopeResolutionFailed = scopeResolutionFailed;
        this.facts = Objects.requireNonNull(facts, "facts");
    }
    /** @return 正在授权的操作对应的请求上下文 */
    public MonitoringRequestContext getRequest() { return request; }
    /** @return 已清洗的资源类别；不可用时为 {@code null} */
    public String getResourceType() { return resourceType; }
    /** @return 已清洗的资源标识；不可用时为 {@code null} */
    public String getResourceId() { return resourceId; }
    /** @return 已清洗的组织或数据域边界；不可用时为 {@code null} */
    public String getOrgScope() { return orgScope; }
    /** @return 可信服务端声明的通行证规则范围 */
    public String getPassRuleId() { return passRuleId; }
    /** @return 可信服务端声明的通行证精确主体 */
    public String getPassSubject() { return passSubject; }
    /** @return whether missing organization ownership must fail closed */
    public boolean isOrgScopeRequired() { return orgScopeRequired; }
    /** @return whether the trusted server-side scope lookup failed */
    public boolean isScopeResolutionFailed() { return scopeResolutionFailed; }
    /** @return complete immutable typed facts used for this authorization decision */
    public ActionFacts getFacts() { return facts; }
}
