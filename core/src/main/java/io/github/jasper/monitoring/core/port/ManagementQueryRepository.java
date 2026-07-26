package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.model.AlertView;
import io.github.jasper.monitoring.api.management.model.ControlView;
import io.github.jasper.monitoring.api.management.model.RuleView;
import io.github.jasper.monitoring.api.management.model.SecurityEventView;
import io.github.jasper.monitoring.api.management.model.WhitelistView;
import io.github.jasper.monitoring.api.management.query.AlertQuery;
import io.github.jasper.monitoring.api.management.query.ControlQuery;
import io.github.jasper.monitoring.api.management.query.RuleQuery;
import io.github.jasper.monitoring.api.management.query.SecurityEventQuery;
import io.github.jasper.monitoring.api.management.query.WhitelistQuery;
import java.util.Optional;

/** Database-backed management projections and optimistic state transitions. */
public interface ManagementQueryRepository {
    ManagementPage<SecurityEventView> searchEvents(String scope, SecurityEventQuery query);
    Optional<SecurityEventView> findEventView(String scope, String id);
    ManagementPage<AlertView> searchAlerts(String scope, AlertQuery query);
    Optional<AlertView> findAlertView(String scope, String id);
    boolean transitionAlert(String scope, String id, long version, String status);
    ManagementPage<RuleView> searchRules(String scope, RuleQuery query);
    Optional<RuleView> findRuleView(String scope, String id);
    ManagementPage<WhitelistView> searchWhitelists(String scope, WhitelistQuery query);
    Optional<WhitelistView> findWhitelistView(String scope, String id);
    boolean transitionWhitelist(String scope, String id, long version, boolean active, String actorId, String reason);
    ManagementPage<ControlView> searchControls(String scope, ControlQuery query);
    Optional<ControlView> findControlView(String scope, String id);
    boolean transitionControl(String scope, String id, long version, String expectedStatus, String targetStatus,
                              String reason);
}
