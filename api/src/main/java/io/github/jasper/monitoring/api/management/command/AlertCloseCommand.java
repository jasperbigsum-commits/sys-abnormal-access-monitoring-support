package io.github.jasper.monitoring.api.management.command;

/** 告警关闭命令（含版本控制）。 */
public final class AlertCloseCommand extends VersionedReasonCommand {
    private AlertCloseCommand(String id, long version, String reason) {
        super(id, version, reason, operationKey("alert-close", id, version));
    }

    /** @return 告警关闭命令对象 */
    public static AlertCloseCommand of(String id, long version, String reason) {
        return new AlertCloseCommand(id, version, reason);
    }
}
