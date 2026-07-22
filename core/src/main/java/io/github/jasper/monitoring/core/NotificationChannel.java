package io.github.jasper.monitoring.core;

/** Best-effort outbound notification port for raised or refreshed alerts. */
public interface NotificationChannel {
    /**
     * Delivers an alert notification. Implementations should throw only for delivery failures;
     * the monitor records the alert regardless.
     */
    void notify(SecurityAlert alert);

    /** @return a channel that intentionally suppresses delivery while preserving monitoring behavior */
    static NotificationChannel noop() {
        return new NotificationChannel() {
            @Override
            public void notify(SecurityAlert alert) {
                // Notifications are intentionally best-effort and never control a business transaction.
            }
        };
    }
}
