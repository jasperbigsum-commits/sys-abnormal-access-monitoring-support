package io.github.jasper.monitoring.core;

import java.time.Instant;

/** Temporary suppression entry for one rule and subject; permanent suppressions are intentionally unsupported. */
public final class WhitelistEntry {
    private final String ruleId;
    private final String subject;
    private final Instant expiresAt;
    /**
     * @param ruleId rule to suppress
     * @param subject exact rule subject to suppress
     * @param expiresAt required expiration timestamp
     */
    public WhitelistEntry(String ruleId, String subject, Instant expiresAt) {
        this.ruleId = ruleId;
        this.subject = subject;
        this.expiresAt = expiresAt;
    }
    public String getRuleId() { return ruleId; }
    public String getSubject() { return subject; }
    public Instant getExpiresAt() { return expiresAt; }
    /** @return whether this entry remains active strictly after {@code instant} */
    public boolean activeAt(Instant instant) { return expiresAt != null && expiresAt.isAfter(instant); }
}
