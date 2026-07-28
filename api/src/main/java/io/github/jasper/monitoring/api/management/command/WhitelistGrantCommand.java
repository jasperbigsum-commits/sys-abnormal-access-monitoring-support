package io.github.jasper.monitoring.api.management.command;

/** 白名单授权放行命令（含版本控制）。 */
public final class WhitelistGrantCommand extends VersionedReasonCommand {
    private WhitelistGrantCommand(String id, long v, String r) {
        super(id, v, r, operationKey("whitelist-grant", id, v));
    }

    /** @return 白名单授权放行命令对象 */
    public static WhitelistGrantCommand of(String id, long v, String r) {
        return new WhitelistGrantCommand(id, v, r);
    }
}
