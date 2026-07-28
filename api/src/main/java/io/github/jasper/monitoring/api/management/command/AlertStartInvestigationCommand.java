package io.github.jasper.monitoring.api.management.command;

/** 告警开始排查命令（含版本控制）。 */
public final class AlertStartInvestigationCommand extends VersionedReasonCommand {
    private AlertStartInvestigationCommand(String id, long version, String reason) {
        super(id, version, reason, operationKey("alert-investigation", id, version));
    }

    /** @return 告警开始排查命令对象 */
    public static AlertStartInvestigationCommand of(String id, long version, String reason) {
        return new AlertStartInvestigationCommand(id, version, reason);
    }
}
