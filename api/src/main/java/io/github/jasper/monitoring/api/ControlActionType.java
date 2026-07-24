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
    REQUIRE_APPROVAL;

    /**
     * 判断该动作是否必须由宿主系统提供控制实现才会产生实际安全效果。
     *
     * <p>{@link #RECORD} 仅保留审计证据，不调用宿主控制逻辑；其他动作均依赖宿主的认证、
     * 授权、会话、限流或审批能力。框架可以为这些动作提供回退触发器，但回退只会记录跳过结果，
     * 不会代替宿主实施控制。</p>
     *
     * @return 需要宿主控制实现时为 {@code true}
     */
    public boolean requiresHostHandler() {
        return this != RECORD;
    }
}
