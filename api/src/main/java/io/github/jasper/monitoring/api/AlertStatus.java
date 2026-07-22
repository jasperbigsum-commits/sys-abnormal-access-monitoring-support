package io.github.jasper.monitoring.api;

/**
 * Current lifecycle state of a security alert.
 *
 * <p>Lifecycle changes are retained as append-only dispositions for auditability.</p>
 */
public enum AlertStatus {
    /** The alert has not yet been triaged. */
    NEW,
    /** An operator has accepted responsibility for triage. */
    ACKNOWLEDGED,
    /** Investigation or remediation is underway. */
    IN_PROGRESS,
    /** The alert was resolved. */
    CLOSED,
    /** The alert was reviewed and determined not to represent a security incident. */
    FALSE_POSITIVE;

    /**
     * Determines whether the alert still requires operational attention.
     *
     * @return {@code true} unless the alert is closed or marked as a false positive
     */
    public boolean isOpen() {
        return this != CLOSED && this != FALSE_POSITIVE;
    }
}
