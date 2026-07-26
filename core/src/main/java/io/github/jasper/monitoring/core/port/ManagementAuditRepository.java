package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.management.ManagementAuditRecord;

/** Append-only persistence boundary for sanitized management audit records. */
public interface ManagementAuditRepository {
    void append(ManagementAuditRecord record);
}
