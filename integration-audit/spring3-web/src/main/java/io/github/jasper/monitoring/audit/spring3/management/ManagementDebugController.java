package io.github.jasper.monitoring.audit.spring3.management;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.SecurityEventQueryService;
import io.github.jasper.monitoring.api.management.query.SecurityEventQuery;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only diagnostics for the admin-web HTTP integration. */
@RestController
@RequestMapping("/audit/management/debug")
public class ManagementDebugController {
    private final MonitoringContextAccessor contexts;
    private final SecurityEventQueryService events;

    public ManagementDebugController(MonitoringContextAccessor contexts, SecurityEventQueryService events) {
        this.contexts = contexts;
        this.events = events;
    }

    @GetMapping("/session")
    public ManagementResult<Map<String, Object>> session() {
        ManagementActor actor = actor();
        requireReadAccess(actor);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("actorId", actor.getActorId());
        result.put("systemScope", actor.getSystemScope());
        result.put("contract", "jeecg-management-v1");
        result.put("serverTime", Instant.now().toString());
        return ManagementResult.ok(result);
    }

    @PostMapping("/request-preview")
    public ManagementResult<Map<String, Object>> preview(@RequestBody RequestPreviewRequest request) {
        ManagementActor actor = actor();
        requireReadAccess(actor);
        int page = request.getPage() == null ? 0 : request.getPage().intValue();
        int size = request.getSize() == null ? 20 : request.getSize().intValue();
        Instant from = ManagementHttpParameters.from(request.getFrom());
        Instant to = ManagementHttpParameters.to(request.getTo());
        ManagementHttpParameters.page(Integer.valueOf(page), Integer.valueOf(size), SecurityEventQuery.Sort.OCCURRED_AT);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("page", Integer.valueOf(page)); result.put("size", Integer.valueOf(size));
        result.put("from", from.toString()); result.put("to", to.toString());
        return ManagementResult.ok(result);
    }

    private void requireReadAccess(ManagementActor actor) {
        events.search(actor, SecurityEventQuery.of(ManagementHttpParameters.page(0, 1, SecurityEventQuery.Sort.OCCURRED_AT),
            Instant.now().minusSeconds(60), Instant.now()));
    }

    private ManagementActor actor() {
        return ManagementActor.of(contexts.identityContext().getUserId(), "audit-spring3-web");
    }

    public static final class RequestPreviewRequest {
        private Integer page; private Integer size; private String from; private String to;
        public Integer getPage() { return page; } public void setPage(Integer value) { page = value; }
        public Integer getSize() { return size; } public void setSize(Integer value) { size = value; }
        public String getFrom() { return from; } public void setFrom(String value) { from = value; }
        public String getTo() { return to; } public void setTo(String value) { to = value; }
    }
}
