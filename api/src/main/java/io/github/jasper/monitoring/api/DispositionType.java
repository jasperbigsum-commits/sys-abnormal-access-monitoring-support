package io.github.jasper.monitoring.api;

/** Immutable audit record types for alert lifecycle decisions. */
public enum DispositionType {
    /** An operator acknowledged the alert. */
    ACKNOWLEDGED,
    /** Investigation or remediation began. */
    IN_PROGRESS,
    /** The alert was resolved. */
    CLOSED,
    /** The alert was classified as a false positive. */
    FALSE_POSITIVE
}
