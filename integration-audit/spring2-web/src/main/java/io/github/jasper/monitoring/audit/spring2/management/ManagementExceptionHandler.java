package io.github.jasper.monitoring.audit.spring2.management;

import io.github.jasper.monitoring.api.error.ManagementAccessDeniedException;
import io.github.jasper.monitoring.api.error.ManagementConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将管理服务的授权拒绝和版本冲突映射为验收 HTTP 响应。
 *
 * <p>本类不重新实现授权策略，也不把拒绝转换为成功；生产宿主可以接入统一错误协议。</p>
 */
@RestControllerAdvice(assignableTypes = {MonitoringManagementController.class,
    WhitelistManagementController.class, RuleManagementController.class, AlertManagementController.class})
public class ManagementExceptionHandler {
    @ExceptionHandler({SecurityException.class, ManagementAccessDeniedException.class})
    public ResponseEntity<?> denied(RuntimeException ignored) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ManagementResult.failure(403, "无权访问管理资源", "FORBIDDEN"));
    }

    @ExceptionHandler(ManagementConflictException.class)
    public ResponseEntity<?> conflict(ManagementConflictException ignored) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ManagementResult.failure(409, "数据版本或状态已变化", "CONFLICT"));
    }

    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<?> validation(IllegalArgumentException ignored) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ManagementResult.failure(422, "请求参数不合法", "VALIDATION"));
    }
}
