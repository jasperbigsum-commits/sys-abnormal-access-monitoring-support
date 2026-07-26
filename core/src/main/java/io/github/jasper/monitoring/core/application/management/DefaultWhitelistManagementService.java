package io.github.jasper.monitoring.core.application.management;

import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementOperation;
import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.WhitelistManagementService;
import io.github.jasper.monitoring.api.management.command.VersionedReasonCommand;
import io.github.jasper.monitoring.api.management.command.WhitelistGrantCommand;
import io.github.jasper.monitoring.api.management.command.WhitelistRevokeCommand;
import io.github.jasper.monitoring.api.management.model.WhitelistView;
import io.github.jasper.monitoring.api.management.query.WhitelistQuery;
import io.github.jasper.monitoring.core.port.ManagementQueryRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import java.util.Objects;

/** Authorized and audited lifecycle operations for predeclared whitelist entries. */
public final class DefaultWhitelistManagementService extends AbstractManagementService
    implements WhitelistManagementService {
    public DefaultWhitelistManagementService(ManagementAccessGuard access, ManagementQueryRepository queries,
                                             MonitoringTransaction transaction) { super(access, queries, transaction); }
    @Override public ManagementPage<WhitelistView> search(final ManagementActor actor, final WhitelistQuery query) {
        Objects.requireNonNull(query, "query"); access.require(actor, ManagementOperation.WHITELIST_READ, "whitelist", "*");
        return transaction.required(() -> { ManagementPage<WhitelistView> page = queries.searchWhitelists(actor.getSystemScope(), query);
            success(actor, ManagementOperation.WHITELIST_READ, "whitelist", "*"); return page; });
    }
    @Override public WhitelistView get(final ManagementActor actor, final String id) {
        access.require(actor, ManagementOperation.WHITELIST_READ, "whitelist", id);
        return transaction.required(() -> { WhitelistView view = require(queries.findWhitelistView(actor.getSystemScope(), id), "whitelist", id);
            success(actor, ManagementOperation.WHITELIST_READ, "whitelist", id); return view; });
    }
    @Override public WhitelistView grant(ManagementActor actor, WhitelistGrantCommand command) {
        return change(actor, ManagementOperation.WHITELIST_GRANT, command, true);
    }
    @Override public WhitelistView revoke(ManagementActor actor, WhitelistRevokeCommand command) {
        return change(actor, ManagementOperation.WHITELIST_REVOKE, command, false);
    }
    private WhitelistView change(final ManagementActor actor, final ManagementOperation operation,
                                 final VersionedReasonCommand command, final boolean active) {
        Objects.requireNonNull(command, "command"); final String id = command.getResourceId();
        access.require(actor, operation, "whitelist", id);
        return transaction.required(() -> { requireUpdated(queries.transitionWhitelist(actor.getSystemScope(), id,
                command.getExpectedVersion(), active, actor.getActorId(), command.getReason()));
            WhitelistView view = require(queries.findWhitelistView(actor.getSystemScope(), id), "whitelist", id);
            success(actor, operation, "whitelist", id); return view; });
    }
}
