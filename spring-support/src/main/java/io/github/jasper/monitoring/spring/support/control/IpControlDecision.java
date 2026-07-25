package io.github.jasper.monitoring.spring.support.control;

import java.time.Duration;
import java.util.Objects;

/**
 * A request enforcement decision that contains no control or detection metadata.
 */
public final class IpControlDecision {
    private static final IpControlDecision ALLOWED = new IpControlDecision(false, false, null);
    private static final IpControlDecision DENIED = new IpControlDecision(true, false, null);

    private final boolean denied;
    private final boolean rateLimited;
    private final Duration retryAfter;

    private IpControlDecision(boolean denied, boolean rateLimited, Duration retryAfter) {
        this.denied = denied;
        this.rateLimited = rateLimited;
        this.retryAfter = retryAfter;
    }

    public static IpControlDecision allowed() {
        return ALLOWED;
    }

    public static IpControlDecision denied() {
        return DENIED;
    }

    public static IpControlDecision rateLimited(Duration retryAfter) {
        if (retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must be positive");
        }
        return new IpControlDecision(false, true, retryAfter);
    }

    public boolean isDenied() {
        return denied;
    }

    public boolean isRateLimited() {
        return rateLimited;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IpControlDecision)) {
            return false;
        }
        IpControlDecision that = (IpControlDecision) other;
        return denied == that.denied
            && rateLimited == that.rateLimited
            && Objects.equals(retryAfter, that.retryAfter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(denied, rateLimited, retryAfter);
    }
}
