package io.github.jasper.monitoring.web;

import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.SecurityFieldSanitizer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Values derived from authenticated server-side request processing only.
 *
 * <p>This context is combined with browser telemetry before conversion into a security event.
 * It prevents a client from asserting its own user, roles, session, or source address.</p>
 */
public final class FrontendServerContext {
    private final String sourceIp;
    private final String userId;
    private final String sessionIdHash;
    private final AccountType accountType;
    private final Set<String> roleIds;
    private FrontendServerContext(Builder builder) {
        sourceIp = required(builder.sourceIp, "sourceIp");
        userId = SecurityFieldSanitizer.text(builder.userId, 128);
        sessionIdHash = SecurityFieldSanitizer.text(builder.sessionIdHash, 256);
        accountType = builder.accountType == null ? AccountType.PERSON : builder.accountType;
        roleIds = Collections.unmodifiableSet(new LinkedHashSet<String>(builder.roleIds));
    }
    /** @return a builder for an immutable server-derived context */
    public static Builder builder() { return new Builder(); }

    /** @return trusted source IP address */
    public String getSourceIp() { return sourceIp; }

    /** @return authenticated user identifier, or {@code null} when anonymous */
    public String getUserId() { return userId; }

    /** @return one-way server-derived session identifier, or {@code null} when unavailable */
    public String getSessionIdHash() { return sessionIdHash; }

    /** @return class of authenticated principal */
    public AccountType getAccountType() { return accountType; }

    /** @return immutable host role identifiers */
    public Set<String> getRoleIds() { return roleIds; }
    private static String required(String value, String name) {
        String sanitized = SecurityFieldSanitizer.text(value, 128);
        if (sanitized == null || sanitized.isEmpty()) {
            throw new MonitoringValidationException(MonitoringErrorCode.REQUIRED_FIELD_MISSING,
                name + " is required");
        }
        return sanitized;
    }
    /** Builder for {@link FrontendServerContext}. */
    public static final class Builder {
        private String sourceIp;
        private String userId;
        private String sessionIdHash;
        private AccountType accountType;
        private Set<String> roleIds = new LinkedHashSet<String>();
        /**
         * Sets the trusted source IP address.
         *
         * @param value trusted source IP address
         * @return this builder
         */
        public Builder sourceIp(String value) { sourceIp = value; return this; }
        /**
         * Sets the authenticated user identifier.
         *
         * @param value authenticated user identifier
         * @return this builder
         */
        public Builder userId(String value) { userId = value; return this; }
        /**
         * Sets a one-way server-derived session identifier.
         *
         * @param value one-way session identifier
         * @return this builder
         */
        public Builder sessionIdHash(String value) { sessionIdHash = value; return this; }
        /**
         * Sets the authenticated principal class.
         *
         * @param value authenticated principal class
         * @return this builder
         */
        public Builder accountType(AccountType value) { accountType = value; return this; }
        /**
         * Sets host role identifiers using a defensive copy.
         *
         * @param value host role identifiers, or {@code null} for no roles
         * @return this builder
         */
        public Builder roleIds(Set<String> value) { roleIds = value == null ? new LinkedHashSet<String>() : new LinkedHashSet<String>(value); return this; }
        /**
         * Builds an immutable context after validating required server facts.
         *
         * @return immutable server context
         * @throws IllegalArgumentException if the source IP is absent
         */
        public FrontendServerContext build() { return new FrontendServerContext(this); }
    }
}
