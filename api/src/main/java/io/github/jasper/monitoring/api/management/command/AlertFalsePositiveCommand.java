package io.github.jasper.monitoring.api.management.command;

/** Versioned command for marking an alert as a false positive. */
public final class AlertFalsePositiveCommand extends VersionedReasonCommand {
    private AlertFalsePositiveCommand(String id, long version, String reason) {
        super(id, version, reason, operationKey("alert-false-positive", id, version));
    }

    public static AlertFalsePositiveCommand of(String id, long version, String reason) {
        return new AlertFalsePositiveCommand(id, version, reason);
    }
}
