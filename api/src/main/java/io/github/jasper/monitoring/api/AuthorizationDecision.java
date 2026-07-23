package io.github.jasper.monitoring.api;

/**
 * 宿主系统资源范围授权检查的不可变结果。
 *
 * <p>拒绝结果可携带稳定且非敏感的原因码，供安全事件和审计记录引用。</p>
 */
public final class AuthorizationDecision {
    private final boolean allowed;
    private final String reasonCode;
    private AuthorizationDecision(boolean allowed, String reasonCode) {
        this.allowed = allowed;
        this.reasonCode = SecurityFieldSanitizer.text(reasonCode, 128);
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
    public static AuthorizationDecision denied(String reasonCode) { return new AuthorizationDecision(false, reasonCode); }

    /** @return 宿主系统是否允许访问目标资源范围 */
    public boolean isAllowed() { return allowed; }

    /** @return 已清洗的拒绝原因码；允许访问时为 {@code null} */
    public String getReasonCode() { return reasonCode; }
}
