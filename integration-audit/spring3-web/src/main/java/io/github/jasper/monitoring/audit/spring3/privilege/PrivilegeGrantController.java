package io.github.jasper.monitoring.audit.spring3.privilege;

import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 参考宿主角色授予边界的 HTTP 适配器。
 *
 * <p>Controller 只负责把当前认证身份、请求参数和请求上下文交给 {@link PrivilegeGrantService}；
 * 自我提权判断、角色关系写入和 PrivilegeChange 监测由 Service 负责。</p>
 */
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
