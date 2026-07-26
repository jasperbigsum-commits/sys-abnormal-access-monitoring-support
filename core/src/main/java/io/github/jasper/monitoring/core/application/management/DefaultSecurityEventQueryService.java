package io.github.jasper.monitoring.core.application.management;

import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementOperation;
import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.SecurityEventQueryService;
import io.github.jasper.monitoring.api.management.model.SecurityEventView;
import io.github.jasper.monitoring.api.management.query.SecurityEventQuery;
import io.github.jasper.monitoring.core.port.ManagementQueryRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import java.util.Objects;

/** Authorized and audited security-event management queries. */
public final class DefaultSecurityEventQueryService extends AbstractManagementService
    implements SecurityEventQueryService {
    public DefaultSecurityEventQueryService(ManagementAccessGuard access, ManagementQueryRepository queries,
                                            MonitoringTransaction transaction) {
        super(access, queries, transaction);
    }
    @Override public ManagementPage<SecurityEventView> search(final ManagementActor actor,
                                                               final SecurityEventQuery query) {
        Objects.requireNonNull(query, "query");
        access.require(actor, ManagementOperation.EVENT_READ, "security-event", "*");
        return transaction.required(() -> {
            ManagementPage<SecurityEventView> page = queries.searchEvents(actor.getSystemScope(), query);
            success(actor, ManagementOperation.EVENT_READ, "security-event", "*");
            return page;
        });
    }
    @Override public SecurityEventView get(final ManagementActor actor, final String eventId) {
        access.require(actor, ManagementOperation.EVENT_READ, "security-event", eventId);
        return transaction.required(() -> {
            SecurityEventView view = require(queries.findEvent(actor.getSystemScope(), eventId), "security-event", eventId);
            success(actor, ManagementOperation.EVENT_READ, "security-event", eventId);
            return view;
        });
    }
}
