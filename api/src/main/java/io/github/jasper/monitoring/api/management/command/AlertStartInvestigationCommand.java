package io.github.jasper.monitoring.api.management.command;

/** Versioned command for starting an alert investigation. */
public final class AlertStartInvestigationCommand extends VersionedReasonCommand {
    private AlertStartInvestigationCommand(String id, long version, String reason) {
        super(id, version, reason, operationKey("alert-investigation", id, version));
    }

    public static AlertStartInvestigationCommand of(String id, long version, String reason) {
        return new AlertStartInvestigationCommand(id, version, reason);
    }
}
