package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.management.command.*; import io.github.jasper.monitoring.api.management.model.WhitelistView; import io.github.jasper.monitoring.api.management.query.*;
/** Versioned lifecycle boundary for whitelist entries predeclared by the host. Grant enables an existing scoped entry; it does not create rule/subject/TTL data from a client request. */
public interface WhitelistManagementService {
    ManagementPage<WhitelistView> search(ManagementActor actor, WhitelistQuery query);
    WhitelistView get(ManagementActor actor, String id);
    WhitelistView grant(ManagementActor actor, WhitelistGrantCommand command);
    WhitelistView revoke(ManagementActor actor, WhitelistRevokeCommand command);
}
