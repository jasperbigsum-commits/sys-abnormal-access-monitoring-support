package io.github.jasper.monitoring.audit.spring3.management;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.ManagementPage;
import io.github.jasper.monitoring.api.management.ManagementPageRequest;
import io.github.jasper.monitoring.api.management.SecurityEventQueryService;
import io.github.jasper.monitoring.api.management.ControlManagementService;
import io.github.jasper.monitoring.api.management.command.ControlExecutionCommand;
import io.github.jasper.monitoring.api.management.model.ControlExecutionView;
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

/**
 * 公开管理服务契约的 HTTP 适配器。
 *
 * <p>The actor is derived from the current monitoring identity and the system scope is fixed
 * 操作者由本宿主当前已认证身份派生，系统范围由本宿主固定。请求体只承载幂等键等操作数据，
 * 不能选择操作者或授权范围。</p>
 */
@RestController
@RequestMapping("/audit/management")
public class MonitoringManagementController {
    private final MonitoringContextAccessor contexts;
    private final SecurityEventQueryService events;
    private final ControlManagementService controls;

    public MonitoringManagementController(MonitoringContextAccessor contexts,
                                          SecurityEventQueryService events, ControlManagementService controls) {
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
        ControlExecutionView execution = controls.execute(actor(), ControlExecutionCommand.of(request.getIdempotencyKey(),
            userId, ControlActionType.REVOKE_SESSION, Instant.now().plusSeconds(300)));
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("status", execution.getStatus());
        body.put("replay", Boolean.valueOf(execution.isIdempotentReplay()));
        return body;
    }

    private ManagementActor actor() {
        return ManagementActor.of(contexts.identityContext().getUserId(), "audit-spring3-web");
    }

    public static final class SessionRevokeRequest {
        private String idempotencyKey;
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }
}
