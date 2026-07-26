package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.management.model.SecurityEventView;
import io.github.jasper.monitoring.api.management.query.*;
/** Read-only event management boundary. Implementations authorize before persistence access and disclose only sanitized fields. */
public interface SecurityEventQueryService {
    ManagementPage<SecurityEventView> search(ManagementActor actor, SecurityEventQuery query);
    SecurityEventView get(ManagementActor actor, String eventId);
}
