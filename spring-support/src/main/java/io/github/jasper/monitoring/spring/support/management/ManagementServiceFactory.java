package io.github.jasper.monitoring.spring.support.management;

import io.github.jasper.monitoring.api.management.ManagementAuthorizer;
import io.github.jasper.monitoring.core.application.management.DefaultAlertManagementService;
import io.github.jasper.monitoring.core.application.management.DefaultControlManagementService;
import io.github.jasper.monitoring.core.application.management.DefaultRuleCatalogService;
import io.github.jasper.monitoring.core.application.management.DefaultSecurityEventQueryService;
import io.github.jasper.monitoring.core.application.management.DefaultWhitelistManagementService;
import io.github.jasper.monitoring.core.application.management.ManagementAccessGuard;
import io.github.jasper.monitoring.core.port.ManagementAuditRepository;
import io.github.jasper.monitoring.core.port.ManagementQueryRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import java.time.Clock;
import java.util.Objects;

/** Builds the five management use cases around one trusted authorization and transaction boundary. */
public final class ManagementServiceFactory {
    private ManagementServiceFactory() { }
    public static ManagementServices create(ManagementAuthorizer authorizer, ManagementQueryRepository queries,
                                            ManagementAuditRepository audits, MonitoringTransaction transaction,
                                            Clock clock) {
        Objects.requireNonNull(authorizer,"authorizer"); Objects.requireNonNull(queries,"queries");
        Objects.requireNonNull(audits,"audits"); Objects.requireNonNull(transaction,"transaction"); Objects.requireNonNull(clock,"clock");
        ManagementAccessGuard guard=new ManagementAccessGuard(authorizer,audits,clock);
        return new ManagementServices(new DefaultSecurityEventQueryService(guard,queries,transaction),
            new DefaultAlertManagementService(guard,queries,transaction),new DefaultRuleCatalogService(guard,queries,transaction),
            new DefaultWhitelistManagementService(guard,queries,transaction),new DefaultControlManagementService(guard,queries,transaction));
    }
}
