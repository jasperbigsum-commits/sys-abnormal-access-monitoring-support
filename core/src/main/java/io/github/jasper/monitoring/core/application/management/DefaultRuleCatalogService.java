package io.github.jasper.monitoring.core.application.management;

import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementOperation;
import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.RuleCatalogService;
import io.github.jasper.monitoring.api.management.command.RuleChangeCommand;
import io.github.jasper.monitoring.api.management.model.RuleView;
import io.github.jasper.monitoring.api.management.query.RuleQuery;
import io.github.jasper.monitoring.core.port.ManagementQueryRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import java.util.Objects;

/** Authorized persisted rule catalog with immutable version changes. */
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
    @Override public RuleView change(final ManagementActor actor, final ManagementActor approver,
                                     final RuleChangeCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(approver, "approver");
        final String ruleId = command.getRuleId();
        access.require(actor, ManagementOperation.RULE_CHANGE, "rule", ruleId);
        if (!actor.getSystemScope().equals(approver.getSystemScope()) || actor.getActorId().equals(approver.getActorId())) {
            access.reject(actor, ManagementOperation.RULE_CHANGE, "rule", ruleId,
                "Rule changes require a distinct approver in the same system scope");
        }
        access.require(approver, ManagementOperation.RULE_APPROVE, "rule", ruleId);
        return transaction.required(() -> {
            boolean changed = queries.changeRule(actor.getSystemScope(), ruleId, command.getExpectedVersion(),
                command.getMode(), command.getThreshold(), actor.getActorId(), approver.getActorId(),
                command.getReason(), command.getIdempotencyKey());
            RuleView view;
            if (changed) {
                view = require(queries.findRuleView(actor.getSystemScope(), ruleId), "rule", ruleId);
            } else {
                view = requireUpdatedOrReplay(queries.findRuleChange(actor.getSystemScope(), ruleId,
                    command.getExpectedVersion(), command.getMode(), command.getThreshold(), actor.getActorId(),
                    approver.getActorId(), command.getReason(), command.getIdempotencyKey()));
            }
            success(approver, ManagementOperation.RULE_APPROVE, "rule", ruleId);
            success(actor, ManagementOperation.RULE_CHANGE, "rule", ruleId);
            return view;
        });
    }

    private RuleView requireUpdatedOrReplay(java.util.Optional<RuleView> replay) {
        requireUpdated(replay.isPresent());
        return replay.get();
    }
}
