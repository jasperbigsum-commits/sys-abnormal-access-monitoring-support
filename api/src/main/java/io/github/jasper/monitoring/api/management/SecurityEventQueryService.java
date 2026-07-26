package io.github.jasper.monitoring.api.management;
import io.github.jasper.monitoring.api.management.model.SecurityEventView;
import io.github.jasper.monitoring.api.management.query.*;
/** Read-only event management boundary. Implementations authorize before persistence access and disclose only sanitized fields. */
public interface SecurityEventQueryService { /** Requires EVENT_READ in actor system scope; bounded query has no side effects and may throw stable management errors. */ ManagementPage<SecurityEventView> search(ManagementActor actor, SecurityEventQuery query); /** Requires EVENT_READ for the event resource before lookup; missing records are reported as ManagementNotFoundException. */ SecurityEventView get(ManagementActor actor, String eventId); }
