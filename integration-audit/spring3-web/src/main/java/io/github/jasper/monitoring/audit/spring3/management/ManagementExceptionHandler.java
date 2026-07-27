package io.github.jasper.monitoring.audit.spring3.management;

import java.util.Collections;
import io.github.jasper.monitoring.api.error.ManagementAccessDeniedException;
import io.github.jasper.monitoring.api.error.ManagementConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps management authorization rejection without duplicating authorization policy. */
@RestControllerAdvice(assignableTypes = {MonitoringManagementController.class,
    WhitelistManagementController.class, RuleManagementController.class, AlertManagementController.class})
public class ManagementExceptionHandler {
    @ExceptionHandler({SecurityException.class, ManagementAccessDeniedException.class})
    public ResponseEntity<?> denied(RuntimeException ignored) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Collections.singletonMap("status", "FORBIDDEN"));
    }

    @ExceptionHandler(ManagementConflictException.class)
    public ResponseEntity<?> conflict(ManagementConflictException ignored) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Collections.singletonMap("status", "CONFLICT"));
    }
}
