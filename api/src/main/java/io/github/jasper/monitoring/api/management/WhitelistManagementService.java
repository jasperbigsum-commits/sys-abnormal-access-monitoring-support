package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.management.command.*; import io.github.jasper.monitoring.api.management.model.WhitelistView; import io.github.jasper.monitoring.api.management.query.*;
/** Versioned whitelist lifecycle boundary. Authorization precedes reads/writes; reasons are bounded and sensitive evidence is excluded. */
public interface WhitelistManagementService {
    ManagementPage<WhitelistView> search(ManagementAuthorizer authorizer, ManagementActor actor, WhitelistQuery query);
    WhitelistView get(ManagementAuthorizer authorizer, ManagementActor actor, String id);
    WhitelistView grant(ManagementAuthorizer authorizer, ManagementActor actor, WhitelistGrantCommand command);
    WhitelistView revoke(ManagementAuthorizer authorizer, ManagementActor actor, WhitelistRevokeCommand command);
}
