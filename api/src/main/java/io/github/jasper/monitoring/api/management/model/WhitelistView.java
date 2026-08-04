package io.github.jasper.monitoring.api.management.model;

import java.time.Instant;

/** 返回给宿主管理适配器的白名单版本化状态视图。 */
public final class WhitelistView {
    private final String id;
    private final String systemScope;
    private final String status;
    private final long version;
    private final String subject;
    private final String ruleId;
    private final Instant expiresAt;
    private final String approvedBy;
    private final String reason;

    private WhitelistView(String id, String systemScope, String status, long version, String subject,
            String ruleId, Instant expiresAt, String approvedBy, String reason) {
        if (id == null || id.trim().isEmpty() || systemScope == null || systemScope.trim().isEmpty()
                || status == null || status.trim().isEmpty() || version < 1L) {
            throw new IllegalArgumentException("invalid whitelist view");
        }
        this.id = id;
        this.systemScope = systemScope;
        this.status = status;
        this.version = version;
        this.subject = subject;
        this.ruleId = ruleId;
        this.expiresAt = expiresAt;
        this.approvedBy = approvedBy;
        this.reason = reason;
    }

    /** @return 白名单视图对象 */
    public static WhitelistView of(String id, String systemScope, String status, long version) {
        return new WhitelistView(id, systemScope, status, version, null, null, null, null, null);
    }

    /** @return 包含通行证匹配范围和审批信息的完整视图 */
    public static WhitelistView of(String id, String systemScope, String status, long version, String subject,
            String ruleId, Instant expiresAt, String approvedBy, String reason) {
        return new WhitelistView(id, systemScope, status, version, subject, ruleId, expiresAt, approvedBy, reason);
    }

    /** @return 兼容旧仓储的默认激活状态白名单视图 */
    public static WhitelistView of(String id, String systemScope) {
        return new WhitelistView(id, systemScope, "ACTIVE", 1L, null, null, null, null, null);
    }

    /** @return 白名单标识 */
    public String getId() {
        return id;
    }

    /** @return 系统作用域 */
    public String getSystemScope() {
        return systemScope;
    }

    /** @return 白名单状态 */
    public String getStatus() {
        return status;
    }

    /** @return 版本号 */
    public long getVersion() {
        return version;
    }

    public String getSubject() { return subject; }
    public String getRuleId() { return ruleId; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getApprovedBy() { return approvedBy; }
    public String getReason() { return reason; }
}
