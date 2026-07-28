package io.github.jasper.monitoring.api.management.command;

/** 控制失败重试命令（含版本控制）。 */
public final class ControlRetryCommand extends VersionedReasonCommand {
    private ControlRetryCommand(String id, long v, String r) {
        super(id, v, r, operationKey("control-retry", id, v));
    }

    /** @return 控制失败重试命令对象 */
    public static ControlRetryCommand of(String id, long v, String r) {
        return new ControlRetryCommand(id, v, r);
    }
}
