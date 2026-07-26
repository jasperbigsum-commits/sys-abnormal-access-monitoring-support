package io.github.jasper.monitoring.core.application.management;

import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementOperation;
import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.RuleCatalogService;
import io.github.jasper.monitoring.api.management.model.RuleView;
import io.github.jasper.monitoring.api.management.query.RuleQuery;
import io.github.jasper.monitoring.core.port.ManagementQueryRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import java.util.Objects;

/** Authorized, read-only view of persisted rule definitions. */
public final class DefaultRuleCatalogService extends AbstractManagementService implements RuleCatalogService {
    public DefaultRuleCatalogService(ManagementAccessGuard access, ManagementQueryRepository queries,
                                     MonitoringTransaction transaction) { super(access, queries, transaction); }
    @Override public ManagementPage<RuleView> search(final ManagementActor actor, final RuleQuery query) {
        Objects.requireNonNull(query, "query");
        access.require(actor, ManagementOperation.RULE_READ, "rule", "*");
        return transaction.required(() -> {
            ManagementPage<RuleView> page = queries.searchRules(actor.getSystemScope(), query);
            success(actor, ManagementOperation.RULE_READ, "rule", "*");
            return page;
        });
    }
    @Override public RuleView get(final ManagementActor actor, final String ruleId) {
        access.require(actor, ManagementOperation.RULE_READ, "rule", ruleId);
        return transaction.required(() -> {
            RuleView view = require(queries.findRuleView(actor.getSystemScope(), ruleId), "rule", ruleId);
            success(actor, ManagementOperation.RULE_READ, "rule", ruleId);
            return view;
        });
    }
}
