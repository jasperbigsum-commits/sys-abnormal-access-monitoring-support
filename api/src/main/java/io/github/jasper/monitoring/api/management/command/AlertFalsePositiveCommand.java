package io.github.jasper.monitoring.api.management.command;

/** 告警误报标记命令（含版本控制）。 */
public final class AlertFalsePositiveCommand extends VersionedReasonCommand {
    private AlertFalsePositiveCommand(String id, long version, String reason) {
        super(id, version, reason, operationKey("alert-false-positive", id, version));
    }

    /** @return 告警误报标记命令对象 */
    public static AlertFalsePositiveCommand of(String id, long version, String reason) {
        return new AlertFalsePositiveCommand(id, version, reason);
    }
}
