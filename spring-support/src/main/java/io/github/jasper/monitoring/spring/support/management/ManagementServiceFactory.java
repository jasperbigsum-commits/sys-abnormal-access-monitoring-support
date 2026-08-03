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
import io.github.jasper.monitoring.core.port.WhitelistRepository;
import java.time.Clock;
import java.util.Objects;
import io.github.jasper.monitoring.core.application.control.ControlExecutionService;

/** Builds the five management use cases around one trusted authorization and transaction boundary. */
public final class ManagementServiceFactory {
    private ManagementServiceFactory() { }
    public static ManagementServices create(ManagementAuthorizer authorizer, ManagementQueryRepository queries,
                                            ManagementAuditRepository audits, MonitoringTransaction transaction,
                                            ControlExecutionService controls, Clock clock) {
        Objects.requireNonNull(authorizer,"authorizer"); Objects.requireNonNull(queries,"queries");
        Objects.requireNonNull(audits,"audits"); Objects.requireNonNull(transaction,"transaction");
        Objects.requireNonNull(controls,"controls"); Objects.requireNonNull(clock,"clock");
        if (!(queries instanceof WhitelistRepository)) {
            throw new IllegalArgumentException("management queries must also provide temporary-pass persistence");
        }
        ManagementAccessGuard guard=new ManagementAccessGuard(authorizer,audits,clock);
        return new ManagementServices(new DefaultSecurityEventQueryService(guard,queries,transaction),
            new DefaultAlertManagementService(guard,queries,transaction),new DefaultRuleCatalogService(guard,queries,transaction),
            new DefaultWhitelistManagementService(guard,queries,transaction),new DefaultControlManagementService(guard,queries,transaction,
                controls,(WhitelistRepository) queries,clock));
    }
}
