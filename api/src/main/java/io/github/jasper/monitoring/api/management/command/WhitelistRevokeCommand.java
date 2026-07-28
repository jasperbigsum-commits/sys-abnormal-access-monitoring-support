package io.github.jasper.monitoring.api.management.command;

/** 白名单撤销命令（含版本控制）。 */
public final class WhitelistRevokeCommand extends VersionedReasonCommand {
    private WhitelistRevokeCommand(String id, long v, String r) {
        super(id, v, r, operationKey("whitelist-revoke", id, v));
    }

    /** @return 白名单撤销命令对象 */
    public static WhitelistRevokeCommand of(String id, long v, String r) {
        return new WhitelistRevokeCommand(id, v, r);
    }
}
