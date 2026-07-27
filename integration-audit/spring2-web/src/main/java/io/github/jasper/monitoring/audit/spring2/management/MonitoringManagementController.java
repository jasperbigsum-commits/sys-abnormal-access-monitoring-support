package io.github.jasper.monitoring.audit.spring2.management;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.ManagementPageRequest;
import io.github.jasper.monitoring.api.management.SecurityEventQueryService;
import io.github.jasper.monitoring.api.management.model.SecurityEventView;
import io.github.jasper.monitoring.api.management.query.SecurityEventQuery;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP adapter over the public management service contract. */
@RestController
@RequestMapping("/audit/management")
public class MonitoringManagementController {
    private final MonitoringContextAccessor contexts;
    private final SecurityEventQueryService events;

    public MonitoringManagementController(MonitoringContextAccessor contexts,
                                          SecurityEventQueryService events) {
        this.contexts = contexts;
        this.events = events;
    }

    @GetMapping("/events")
    public Map<String, Object> events() {
        ManagementActor actor = ManagementActor.of(contexts.identityContext().getUserId(), "audit-spring2-web");
        SecurityEventQuery query = SecurityEventQuery.of(
            ManagementPageRequest.of(0, 20, SecurityEventQuery.Sort.OCCURRED_AT),
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(1));
        ManagementPage<SecurityEventView> page = events.search(actor, query);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("count", Long.valueOf(page.getTotalElements()));
        return body;
    }
}
