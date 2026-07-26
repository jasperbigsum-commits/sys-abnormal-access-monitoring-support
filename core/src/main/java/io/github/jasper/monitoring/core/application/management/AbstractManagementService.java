package io.github.jasper.monitoring.core.application.management;

import io.github.jasper.monitoring.api.error.ManagementConflictException;
import io.github.jasper.monitoring.api.error.ManagementNotFoundException;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementOperation;
import io.github.jasper.monitoring.core.domain.management.ManagementAuditRecord;
import io.github.jasper.monitoring.core.port.ManagementQueryRepository;
import io.github.jasper.monitoring.core.port.MonitoringTransaction;
import java.util.Objects;
import java.util.Optional;

abstract class AbstractManagementService {
    protected final ManagementAccessGuard access;
    protected final ManagementQueryRepository queries;
    protected final MonitoringTransaction transaction;

    AbstractManagementService(ManagementAccessGuard access, ManagementQueryRepository queries,
                              MonitoringTransaction transaction) {
        this.access = Objects.requireNonNull(access, "access");
        this.queries = Objects.requireNonNull(queries, "queries");
        this.transaction = Objects.requireNonNull(transaction, "transaction");
    }

    protected <T> T require(Optional<T> value, String type, String id) {
        if (!value.isPresent()) throw new ManagementNotFoundException(type + " was not found: " + id);
        return value.get();
    }

    protected void requireUpdated(boolean updated) {
        if (!updated) throw new ManagementConflictException("The resource changed before the command was applied");
    }

    protected void success(ManagementActor actor, ManagementOperation operation, String type, String id) {
        access.audit(actor, operation, type, id, ManagementAuditRecord.Outcome.SUCCEEDED);
    }
}
