package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.management.command.*; import io.github.jasper.monitoring.api.management.model.WhitelistView; import io.github.jasper.monitoring.api.management.query.*;
/** Versioned whitelist lifecycle boundary. Authorization precedes reads/writes; reasons are bounded and sensitive evidence is excluded. */
public interface WhitelistManagementService {
    ManagementPage<WhitelistView> search(ManagementActor actor, WhitelistQuery query);
    WhitelistView get(ManagementActor actor, String id);
    WhitelistView grant(ManagementActor actor, WhitelistGrantCommand command);
    WhitelistView revoke(ManagementActor actor, WhitelistRevokeCommand command);
}
