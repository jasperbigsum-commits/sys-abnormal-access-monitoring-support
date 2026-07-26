package io.github.jasper.monitoring.api.management.command;

/** Versioned command for closing an alert. */
public final class AlertCloseCommand extends VersionedReasonCommand {
    private AlertCloseCommand(String id, long version, String reason) {
        super(id, version, reason, operationKey("alert-close", id, version));
    }

    public static AlertCloseCommand of(String id, long version, String reason) {
        return new AlertCloseCommand(id, version, reason);
    }
}
