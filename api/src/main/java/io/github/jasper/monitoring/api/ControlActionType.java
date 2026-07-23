package io.github.jasper.monitoring.api;

/**
 * 监测组件可请求宿主控制处理器执行的响应动作。
 */
public enum ControlActionType {
    /** 仅保留证据，不改变请求处理结果。 */
    RECORD,
    /** 在下一次敏感操作前要求完成挑战校验。 */
    REQUIRE_CAPTCHA,
    /** 临时限制请求频率。 */
    RATE_LIMIT,
    /** 按宿主策略锁定账号或主体。 */
    LOCK,
    /** 使一个或多个活跃会话失效。 */
    REVOKE_SESSION,
    /** 要求完成更强的身份验证步骤。 */
    REQUIRE_MFA,
    /** 拒绝关联请求或资源范围访问。 */
    DENY,
    /** 要求经过宿主定义的审批流程。 */
    REQUIRE_APPROVAL
}
