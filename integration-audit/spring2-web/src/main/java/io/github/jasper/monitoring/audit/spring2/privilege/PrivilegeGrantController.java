package io.github.jasper.monitoring.audit.spring2.privilege;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the reference host's role grant boundary. */
@RestController
@RequestMapping("/audit/privileges")
public final class PrivilegeGrantController {
    private final PrivilegeGrantService grants;
    private final MonitoringContextAccessor context;

    public PrivilegeGrantController(PrivilegeGrantService grants, MonitoringContextAccessor context) {
        this.grants = grants;
        this.context = context;
    }

    @PostMapping("/{targetUserId}/roles/{roleId}")
    public ResponseEntity<Void> grant(@PathVariable("targetUserId") String targetUserId,
                                      @PathVariable("roleId") String roleId) {
        return grants.grant(context.identityContext(), context.requestContext(), targetUserId, roleId)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}
