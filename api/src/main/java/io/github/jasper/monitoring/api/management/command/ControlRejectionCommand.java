package io.github.jasper.monitoring.api.management.command;

/** 控制驳回命令（含版本控制）。 */
public final class ControlRejectionCommand extends VersionedReasonCommand {
    private ControlRejectionCommand(String id, long v, String r) {
        super(id, v, r, operationKey("control-rejection", id, v));
    }

    /** @return 控制驳回命令对象 */
    public static ControlRejectionCommand of(String id, long v, String r) {
        return new ControlRejectionCommand(id, v, r);
    }
}
