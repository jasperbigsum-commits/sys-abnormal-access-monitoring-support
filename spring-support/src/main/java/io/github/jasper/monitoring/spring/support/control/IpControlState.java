package io.github.jasper.monitoring.spring.support.control;

import io.github.jasper.monitoring.api.ControlActionType;
import java.time.Instant;

/**
 * Stores active controls for canonical IP addresses.
 *
 * <p>This bounded state deduplicates a key while its control remains active. Durable replay protection across
 * expiry, restart, or nodes belongs to the durable control execution store.</p>
 */
public interface IpControlState {
    enum ActivationResult {
        ACTIVATED,
        IDEMPOTENT_REPLAY,
        CAPACITY_REJECTED,
        EXPIRED
    }

    ActivationResult activate(String idempotencyKey, String canonicalIp,
                              ControlActionType action, Instant expiresAt, Instant now);

    IpControlDecision check(String canonicalIp, Instant now);
}
