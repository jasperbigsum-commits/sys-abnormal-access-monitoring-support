package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.management.command.RuleChangeCommand;
import io.github.jasper.monitoring.api.management.model.RuleView; import io.github.jasper.monitoring.api.management.query.*;
/**
 * Authorized management boundary for persisted rule definitions.
 *
 * <p>Changes append immutable versions and do not mutate the rule catalog
 * frozen by a running monitoring instance. The host must publish/restart its
 * configured runtime before a persisted change becomes effective.</p>
 */
public interface RuleCatalogService {
    /** Lists the latest persisted version of each rule visible in the actor's scope. */
    ManagementPage<RuleView> search(ManagementActor actor, RuleQuery query);
    /** Returns the latest persisted rule version after authorization. */
    RuleView get(ManagementActor actor, String ruleId);
    /**
     * Appends an approved version or fails with a management conflict when the expected version is stale.
     * Both actors must come from trusted server-side authentication; the service requires a distinct approver
     * in the same scope and authorizes {@link ManagementOperation#RULE_APPROVE} separately.
     */
    RuleView change(ManagementActor actor, ManagementActor approver, RuleChangeCommand command);
}
