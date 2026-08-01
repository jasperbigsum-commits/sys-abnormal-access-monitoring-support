package io.github.jasper.monitoring.api;

import io.github.jasper.monitoring.api.code.ReasonCode;

import java.util.Objects;

/**
 * 宿主系统资源范围授权检查的不可变结果。
 *
 * <p>拒绝结果可携带稳定且非敏感的原因码，供安全事件和审计记录引用。</p>
 */
public final class AuthorizationDecision {
    private final boolean allowed;
    private final ReasonCode reason;
    private AuthorizationDecision(boolean allowed, ReasonCode reason) {
        this.allowed = allowed;
        this.reason = allowed ? null : Objects.requireNonNull(reason, "reason");
    }
    /**
     * 创建允许访问的决策，不包含拒绝原因。
     *
     * @return 允许访问的决策
     */
    public static AuthorizationDecision allowed() { return new AuthorizationDecision(true, null); }

    /**
     * 创建拒绝访问的决策。
     *
     * @param reasonCode 说明拒绝原因的稳定、非敏感代码
     * @return 拒绝访问的决策
     */
    public static AuthorizationDecision denied(ReasonCode reason) { return new AuthorizationDecision(false, reason); }

    /** @return 宿主系统是否允许访问目标资源范围 */
    public boolean isAllowed() { return allowed; }

    /** @return typed rejection reason; allowed decisions return {@code null} */
    public ReasonCode getReason() { return reason; }
}
