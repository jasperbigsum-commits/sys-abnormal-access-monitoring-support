package io.github.jasper.monitoring.core.domain;


import java.time.Instant;

/**
 * 针对一个规则和主体的临时抑制记录。
 *
 * <p>有意不支持永久抑制，以免管理配置长期掩盖新的异常信号。</p>
 */
public final class WhitelistEntry {
    private final String whitelistId;
    private final String systemId;
    private final String ruleId;
    private final String subject;
    private final Instant expiresAt;
    private final String approvedBy;
    private final String reason;
    /**
     * @param ruleId 待抑制的规则标识
     * @param subject 待抑制的精确规则主体
     * @param expiresAt 必填的失效时间
     */
    public WhitelistEntry(String ruleId, String subject, Instant expiresAt) {
        this(null, null, ruleId, subject, expiresAt, "SYSTEM", "Temporary rule exemption");
    }

    private WhitelistEntry(String whitelistId, String systemId, String ruleId, String subject, Instant expiresAt,
            String approvedBy, String reason) {
        this.whitelistId = whitelistId;
        this.systemId = systemId;
        this.ruleId = required(ruleId, "ruleId");
        this.subject = required(subject, "subject");
        this.expiresAt = java.util.Objects.requireNonNull(expiresAt, "expiresAt");
        this.approvedBy = required(approvedBy, "approvedBy");
        this.reason = required(reason, "reason");
    }
    /** 创建可被管理端查询和撤销的审批通行证。 */
    public static WhitelistEntry issued(String whitelistId, String systemId, String ruleId, String subject,
                                        Instant expiresAt) {
        return issued(whitelistId, systemId, ruleId, subject, expiresAt, "SYSTEM", "Temporary pass approved");
    }

    /** 创建包含审批审计信息的临时通行证。 */
    public static WhitelistEntry issued(String whitelistId, String systemId, String ruleId, String subject,
                                        Instant expiresAt, String approvedBy, String reason) {
        if (whitelistId == null || whitelistId.trim().isEmpty() || systemId == null || systemId.trim().isEmpty()) {
            throw new IllegalArgumentException("whitelistId and systemId are required");
        }
        return new WhitelistEntry(whitelistId, systemId, ruleId, subject, expiresAt, approvedBy, reason);
    }
    /** @return 管理侧通行证标识；旧式规则豁免可为 {@code null} */
    public String getWhitelistId() { return whitelistId; }
    /** @return 通行证所属系统；旧式规则豁免可为 {@code null} */
    public String getSystemId() { return systemId; }
    /** @return 被抑制的规则标识 */
    public String getRuleId() { return ruleId; }
    /** @return 被抑制的精确规则主体 */
    public String getSubject() { return subject; }
    /** @return 临时抑制失效时间 */
    public Instant getExpiresAt() { return expiresAt; }
    /** @return 签发通行证的可信管理操作者 */
    public String getApprovedBy() { return approvedBy; }
    /** @return 审批原因 */
    public String getReason() { return reason; }
    /**
     * @param instant 判断时间
     * @return 该记录是否在 {@code instant} 之后仍保持有效
     */
    public boolean activeAt(Instant instant) { return expiresAt != null && expiresAt.isAfter(instant); }

    private static String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
