package io.github.jasper.monitoring.api.management.command;

/** 告警处置命令（含版本控制）。 */
public final class AlertDispositionCommand extends VersionedReasonCommand {
    private AlertDispositionCommand(String id, long v, String r) {
        super(id, v, r, operationKey("alert-disposition", id, v));
    }

    /** @return 告警处置命令对象 */
    public static AlertDispositionCommand of(String id, long v, String r) {
        return new AlertDispositionCommand(id, v, r);
    }

    /** @return 告警标识 */
    public String getAlertId() {
        return getResourceId();
    }
}
