package io.github.jasper.monitoring.audit.spring2.monitoring;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.action.MonitorAction;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.fact.ActionFacts;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.audit.spring2.AuditExportRequest;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Monitoring-only endpoints used to verify explicit and annotated instrumentation. */
@RestController
@RequestMapping("/audit")
public class MonitoringFixtureController {
    private static final long SERVER_REPORTED_ROW_COUNT = 37L;
    private final MonitoringService monitoring;
    private final MonitoringContextAccessor contexts;

    public MonitoringFixtureController(MonitoringService monitoring, MonitoringContextAccessor contexts) {
        this.monitoring = monitoring;
        this.contexts = contexts;
    }

    @PostMapping("/login-failure")
    public Map<String, Object> loginFailure() {
        return response(monitoring.monitor(ActionExecution.of(BuiltInActions.LoginFailure.class,
            contexts.requestContext(), contexts.identityContext(), ActionOutcome.failure(
                "INVALID_PASSWORD", ActionOutcome.ExceptionClassification.AUTHORIZATION, 0L))));
    }

    @PostMapping("/export")
    public Map<String, Object> export() {
        ActionFacts facts = ActionFacts.builder()
            .put(BuiltInFacts.ResourceId.class, "audit-export-2026")
            .put(BuiltInFacts.DataCount.class, Long.valueOf(5000L)).build();
        return response(monitoring.monitor(ActionExecution.of(BuiltInActions.ReportExport.class,
            contexts.requestContext(), contexts.identityContext(), ActionOutcome.success(0L),
            facts, FactSource.HOST_PROVIDER)));
    }

    @GetMapping("/annotated-query")
    @MonitorAction(BuiltInActions.Query.class)
    public Map<String, Object> annotatedQuery() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("status", "ok");
        return body;
    }

    @PostMapping("/annotated-export")
    @MonitorAction(BuiltInActions.Query.class)
    public Map<String, Object> annotatedExport(@RequestBody AuditExportRequest ignored) {
        return exportResponse();
    }

    @PostMapping("/annotated-export-denied")
    public ResponseEntity<Map<String, Object>> annotatedExportDenied(@RequestBody AuditExportRequest ignored) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(exportResponse());
    }

    private static Map<String, Object> exportResponse() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("rowCount", Long.valueOf(SERVER_REPORTED_ROW_COUNT));
        return body;
    }

    private static Map<String, Object> response(SecurityEventAssembler.AssemblyResult outcome) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("eventId", outcome.getEvent().getEventId());
        body.put("action", outcome.getEvent().getAction());
        return body;
    }
}
