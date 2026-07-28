package io.github.jasper.monitoring.api.management.command;

/** 控制审批命令（含版本控制）。 */
public final class ControlApprovalCommand extends VersionedReasonCommand {
    private ControlApprovalCommand(String id, long v, String r) {
        super(id, v, r, operationKey("control-approval", id, v));
    }

    /** @return 控制审批命令对象 */
    public static ControlApprovalCommand of(String id, long v, String r) {
        return new ControlApprovalCommand(id, v, r);
    }
}
