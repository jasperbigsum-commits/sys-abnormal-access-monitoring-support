package io.github.jasper.monitoring.spring.support.management;

import io.github.jasper.monitoring.api.management.AlertManagementService;
import io.github.jasper.monitoring.api.management.ControlManagementService;
import io.github.jasper.monitoring.api.management.RuleCatalogService;
import io.github.jasper.monitoring.api.management.SecurityEventQueryService;
import io.github.jasper.monitoring.api.management.WhitelistManagementService;

/** Immutable group of controller-ready management service boundaries. */
public final class ManagementServices {
    private final SecurityEventQueryService events;
    private final AlertManagementService alerts;
    private final RuleCatalogService rules;
    private final WhitelistManagementService whitelists;
    private final ControlManagementService controls;

    ManagementServices(SecurityEventQueryService events, AlertManagementService alerts, RuleCatalogService rules,
                       WhitelistManagementService whitelists, ControlManagementService controls) {
        this.events=events; this.alerts=alerts; this.rules=rules; this.whitelists=whitelists; this.controls=controls;
    }
    public SecurityEventQueryService events() { return events; }
    public AlertManagementService alerts() { return alerts; }
    public RuleCatalogService rules() { return rules; }
    public WhitelistManagementService whitelists() { return whitelists; }
    public ControlManagementService controls() { return controls; }
}
