package io.github.jasper.monitoring.core.domain;


import java.time.Instant;

/**
 * 针对一个规则和主体的临时抑制记录。
 *
 * <p>有意不支持永久抑制，以免管理配置长期掩盖新的异常信号。</p>
 */
public final class WhitelistEntry {
    private final String ruleId;
    private final String subject;
    private final Instant expiresAt;
    /**
     * @param ruleId 待抑制的规则标识
     * @param subject 待抑制的精确规则主体
     * @param expiresAt 必填的失效时间
     */
    public WhitelistEntry(String ruleId, String subject, Instant expiresAt) {
        this.ruleId = ruleId;
        this.subject = subject;
        this.expiresAt = expiresAt;
    }
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
