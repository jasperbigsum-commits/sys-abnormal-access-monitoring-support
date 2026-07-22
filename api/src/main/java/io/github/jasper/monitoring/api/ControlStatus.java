package io.github.jasper.monitoring.api;

/** Outcome reported after the host attempts a requested control action. */
public enum ControlStatus {
    /** The action was applied successfully. */
    SUCCEEDED,
    /** The action could not be applied; the failure must remain auditable. */
    FAILED,
    /** The action was intentionally not applied, for example due to deduplication. */
    SKIPPED
}
