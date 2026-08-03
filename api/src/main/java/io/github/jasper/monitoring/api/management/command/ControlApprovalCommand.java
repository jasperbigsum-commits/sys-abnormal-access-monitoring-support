package io.github.jasper.monitoring.api.management.command;

import java.time.Instant;

/** 控制审批命令（含版本控制）。 */
public final class ControlApprovalCommand extends VersionedReasonCommand {
    private final Instant passExpiresAt;

    private ControlApprovalCommand(String id, long v, String r, Instant passExpiresAt) {
        super(id, v, r, operationKey("control-approval", id, v));
        this.passExpiresAt = passExpiresAt;
    }

    /** @return 控制审批命令对象 */
    public static ControlApprovalCommand of(String id, long v, String r) {
        return new ControlApprovalCommand(id, v, r, null);
    }

    /**
     * 审批并为相同规则主体签发一张临时通行证。省略到期时间表示仅审批当前控制。
     *
     * @param passExpiresAt 通行证到期时间，必须由服务端校验为未来时间
     * @return 控制审批命令对象
     */
    public static ControlApprovalCommand withPassUntil(String id, long v, String r, Instant passExpiresAt) {
        if (passExpiresAt == null) throw new IllegalArgumentException("passExpiresAt is required");
        return new ControlApprovalCommand(id, v, r, passExpiresAt);
    }

    /** @return 临时通行证到期时间；未选择通行证时为 {@code null} */
    public Instant getPassExpiresAt() { return passExpiresAt; }
}
