package io.github.jasper.monitoring.api.management.command;

/** Versioned acknowledgement of an alert. */
public final class AlertAcknowledgeCommand extends VersionedReasonCommand {
    private AlertAcknowledgeCommand(String id, long version, String reason, String key) {
        super(id, version, reason, key);
    }

    public static AlertAcknowledgeCommand of(String id, long version, String reason, String key) {
        return new AlertAcknowledgeCommand(id, version, reason, key);
    }

    public String getAlertId() { return getResourceId(); }
}
