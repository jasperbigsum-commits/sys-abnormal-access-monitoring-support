package io.github.jasper.monitoring.api;

/** Final server-side outcome of the action represented by a security event. */
public enum SecurityEventResult {
    /** The action completed successfully. */
    SUCCESS,
    /** The action failed before completion. */
    FAILURE,
    /** The action was rejected by authorization or a security control. */
    DENIED
}
