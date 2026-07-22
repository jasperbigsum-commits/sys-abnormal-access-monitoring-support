package io.github.jasper.monitoring.core;

import io.github.jasper.monitoring.api.ControlActionType;

/**
 * Host integration point for concrete rate-limit, session, lock, MFA, or deny actions.
 * Implementations must apply only the supplied command scope and return a non-null result.
 */
public interface ControlHandler {
    /** @return {@code true} when this handler can execute the requested action type */
    boolean supports(ControlActionType action);

    /**
     * Applies a control action in the host system.
     *
     * @param command immutable, idempotent control instruction
     * @return non-null execution result; use {@link ControlExecution#failed(String, String)} for handled failures
     */
    ControlExecution execute(ControlCommand command);
}
