package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.management.model.RuleView; import io.github.jasper.monitoring.api.management.query.*;
public interface RuleCatalogService { ManagementPage<RuleView> search(ManagementActor actor, RuleQuery query); RuleView get(ManagementActor actor, String ruleId); }
