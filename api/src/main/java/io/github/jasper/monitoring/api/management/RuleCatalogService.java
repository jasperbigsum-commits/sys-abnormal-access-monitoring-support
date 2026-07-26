package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.management.model.RuleView; import io.github.jasper.monitoring.api.management.query.*;
/** Read-only frozen rule catalog; no runtime toggles are exposed. */
public interface RuleCatalogService { /** Requires RULE_READ in actor system scope before querying; only minimal rule metadata is disclosed. */ ManagementPage<RuleView> search(ManagementActor actor, RuleQuery query); /** Requires RULE_READ before lookup; may throw stable not-found/access errors. */ RuleView get(ManagementActor actor, String ruleId); }
