package io.github.jasper.monitoring.audit.spring3.management;

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

/** HTTP adapter for predeclared, versioned whitelist lifecycle operations. */
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
        return ManagementActor.of(contexts.identityContext().getUserId(), "audit-spring3-web");
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
