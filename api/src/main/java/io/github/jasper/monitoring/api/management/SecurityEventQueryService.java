package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.management.model.SecurityEventView;
import io.github.jasper.monitoring.api.management.query.*;
public interface SecurityEventQueryService { ManagementPage<SecurityEventView> search(ManagementActor actor, SecurityEventQuery query); SecurityEventView get(ManagementActor actor, String eventId); }
