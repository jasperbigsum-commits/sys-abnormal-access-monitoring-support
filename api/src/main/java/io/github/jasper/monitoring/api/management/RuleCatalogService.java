package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.management.model.RuleView; import io.github.jasper.monitoring.api.management.query.*;
/** Read-only frozen rule catalog; no runtime toggles are exposed. */
public interface RuleCatalogService {
    ManagementPage<RuleView> search(ManagementAuthorizer authorizer, ManagementActor actor, RuleQuery query);
    RuleView get(ManagementAuthorizer authorizer, ManagementActor actor, String ruleId);
}
