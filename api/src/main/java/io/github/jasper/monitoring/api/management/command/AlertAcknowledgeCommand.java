package io.github.jasper.monitoring.api.management.command;

/** 告警确认命令（含版本控制）。 */
public final class AlertAcknowledgeCommand extends VersionedReasonCommand {
    private AlertAcknowledgeCommand(String id, long version, String reason, String key) {
        super(id, version, reason, key);
    }

    /** @return 告警确认命令对象 */
    public static AlertAcknowledgeCommand of(String id, long version, String reason, String key) {
        return new AlertAcknowledgeCommand(id, version, reason, key);
    }

    /** @return 告警标识 */
    public String getAlertId() { return getResourceId(); }
}
