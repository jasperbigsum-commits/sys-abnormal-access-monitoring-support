package io.github.jasper.monitoring.web;

import java.time.Duration;
import java.time.Instant;

/**
 * Validates time plausibility for browser-provided supplemental evidence.
 *
 * <p>This validator does not establish identity or authorization. Call it before mapping a signal
 * and use a server-generated receipt time.</p>
 */
public final class FrontendSignalValidator {
    private final Duration allowedClockSkew;
    /**
     * Creates a validator with the maximum permitted browser/server timestamp difference.
     *
     * @param allowedClockSkew non-negative permitted difference
     * @throws IllegalArgumentException if the duration is {@code null} or negative
     */
    public FrontendSignalValidator(Duration allowedClockSkew) {
        if (allowedClockSkew == null || allowedClockSkew.isNegative()) { throw new IllegalArgumentException("allowedClockSkew must be non-negative"); }
        this.allowedClockSkew = allowedClockSkew;
    }
    /**
     * Validates that a signal was generated close enough to its server receipt time.
     *
     * @param signal browser signal to check
     * @param receivedAt authoritative server receipt time
     * @throws IllegalArgumentException if either input is missing or clock skew exceeds the limit
     */
    public void validate(FrontendSignal signal, Instant receivedAt) {
        if (signal == null || receivedAt == null) { throw new IllegalArgumentException("signal and receivedAt are required"); }
        Duration skew = Duration.between(signal.getOccurredAt(), receivedAt).abs();
        if (skew.compareTo(allowedClockSkew) > 0) { throw new IllegalArgumentException("Frontend signal is outside allowed clock skew"); }
    }
}
