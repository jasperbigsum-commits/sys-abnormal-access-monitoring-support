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
    /**
     * @param ruleId 待抑制的规则标识
     * @param subject 待抑制的精确规则主体
     * @param expiresAt 必填的失效时间
     */
    public WhitelistEntry(String ruleId, String subject, Instant expiresAt) {
        this(null, null, ruleId, subject, expiresAt);
    }

    private WhitelistEntry(String whitelistId, String systemId, String ruleId, String subject, Instant expiresAt) {
        this.whitelistId = whitelistId;
        this.systemId = systemId;
        this.ruleId = ruleId;
        this.subject = subject;
        this.expiresAt = expiresAt;
    }
    /** 创建可被管理端查询和撤销的审批通行证。 */
    public static WhitelistEntry issued(String whitelistId, String systemId, String ruleId, String subject,
                                        Instant expiresAt) {
        if (whitelistId == null || whitelistId.trim().isEmpty() || systemId == null || systemId.trim().isEmpty()) {
            throw new IllegalArgumentException("whitelistId and systemId are required");
        }
        return new WhitelistEntry(whitelistId, systemId, ruleId, subject, expiresAt);
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
    /**
     * @param instant 判断时间
     * @return 该记录是否在 {@code instant} 之后仍保持有效
     */
    public boolean activeAt(Instant instant) { return expiresAt != null && expiresAt.isAfter(instant); }
}
