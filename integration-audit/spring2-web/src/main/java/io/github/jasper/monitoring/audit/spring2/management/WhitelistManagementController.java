package io.github.jasper.monitoring.audit.spring2.management;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.management.ManagementActor;
import io.github.jasper.monitoring.api.management.WhitelistManagementService;
import io.github.jasper.monitoring.api.management.command.WhitelistGrantCommand;
import io.github.jasper.monitoring.api.management.command.WhitelistRevokeCommand;
import io.github.jasper.monitoring.api.management.model.WhitelistView;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预声明白名单生命周期操作的 HTTP 适配器。
 *
 * <p>授予和撤销必须携带期望版本，操作者从服务端身份派生；白名单范围、授权、版本冲突和审计
 * 由公开管理服务负责。</p>
 */
@RestController
@RequestMapping("/audit/management/whitelists")
public class WhitelistManagementController {
    private final MonitoringContextAccessor contexts;
    private final WhitelistManagementService whitelists;

    public WhitelistManagementController(MonitoringContextAccessor contexts,
                                         WhitelistManagementService whitelists) {
        this.contexts = contexts;
        this.whitelists = whitelists;
    }

    @PostMapping("/{id}/grant")
    public WhitelistView grant(@PathVariable("id") String id, @RequestBody VersionedReasonRequest request) {
        return whitelists.grant(actor(), WhitelistGrantCommand.of(id, request.getExpectedVersion(),
            request.getReason()));
    }

    @PostMapping("/{id}/revoke")
    public WhitelistView revoke(@PathVariable("id") String id, @RequestBody VersionedReasonRequest request) {
        return whitelists.revoke(actor(), WhitelistRevokeCommand.of(id, request.getExpectedVersion(),
            request.getReason()));
    }

    private ManagementActor actor() {
        return ManagementActor.of(contexts.identityContext().getUserId(), "audit-spring2-web");
    }

    public static class VersionedReasonRequest {
        private long expectedVersion;
        private String reason;
        public long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(long expectedVersion) { this.expectedVersion = expectedVersion; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
