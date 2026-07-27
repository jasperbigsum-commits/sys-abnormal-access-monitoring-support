package io.github.jasper.monitoring.audit.spring3.management;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.core.application.control.ControlExecutionService;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;

/** Thin HTTP adapter over the public management service contract. */
@RestController
@RequestMapping("/audit/management")
public class MonitoringManagementController {
    private final MonitoringContextAccessor contexts;
    private final SecurityEventQueryService events;
    private final ControlExecutionService controls;

    public MonitoringManagementController(MonitoringContextAccessor contexts,
                                          SecurityEventQueryService events, ControlExecutionService controls) {
        this.contexts = contexts;
        this.events = events;
        this.controls = controls;
    }

    @GetMapping("/events")
    public Map<String, Object> events() {
        ManagementActor actor = ManagementActor.of(contexts.identityContext().getUserId(), "audit-spring3-web");
        SecurityEventQuery query = SecurityEventQuery.of(
            ManagementPageRequest.of(0, 20, SecurityEventQuery.Sort.OCCURRED_AT),
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(1));
        ManagementPage<SecurityEventView> page = events.search(actor, query);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("count", Long.valueOf(page.getTotalElements()));
        return body;
    }

    @PostMapping("/sessions/{userId}/revoke")
    public Map<String, Object> revokeSessions(@PathVariable("userId") String userId,
                                              @RequestBody SessionRevokeRequest request) {
        ControlExecution execution = controls.execute(new ControlCommand(request.getIdempotencyKey(),
            "manual-session-revoke", userId, ControlActionType.REVOKE_SESSION,
            Instant.now().plusSeconds(300), "TC-11"));
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("status", execution.getStatus().name());
        body.put("replay", Boolean.valueOf(execution.isIdempotentReplay()));
        return body;
    }

    public static final class SessionRevokeRequest {
        private String idempotencyKey;
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }
}
