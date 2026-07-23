package io.github.jasper.monitoring.api;

/**
 * 由宿主系统提供的资源级授权决策权威接口。
 *
 * <p>监测组件可以记录该决策，但绝不会据此扩大已授权范围。</p>
 */
public interface ResourceScopeAuthorizer {
    /**
     * 判断一个身份是否可访问目标资源范围。
     *
     * @param identity 由宿主后端解析的可信身份
     * @param request 待授权的请求和资源事实
     * @return 明确的允许或拒绝决策；不确定时实现应采用默认拒绝
     */
    AuthorizationDecision authorize(IdentityContext identity, ResourceScopeRequest request);
}
