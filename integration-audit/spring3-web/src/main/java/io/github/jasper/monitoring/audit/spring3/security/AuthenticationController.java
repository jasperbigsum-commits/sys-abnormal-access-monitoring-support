package io.github.jasper.monitoring.audit.spring3.security;

import java.util.LinkedHashMap;
import java.util.Map;
import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Anonymous login and session probes for the stateful reference host. */
@RestController
@RequestMapping("/audit/authentication")
public class AuthenticationController {
    private final AuditAuthenticationService authentication; private final AuditSessionService sessions;
    private final MonitoringContextAccessor contexts;
    public AuthenticationController(AuditAuthenticationService authentication, AuditSessionService sessions,
                                    MonitoringContextAccessor contexts) {
        this.authentication=authentication; this.sessions=sessions; this.contexts=contexts;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body) {
        AuditAuthenticationService.AuthenticationResult result = authentication.authenticate(
            body.getUserId(), body.isAccepted(), contexts.requestContext().getSourceIp());
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("status", result.getStatus());
        if (result.getReason()!=null) response.put("reason", result.getReason());
        if (result.getSessionId()!=null) response.put("sessionId", result.getSessionId());
        HttpStatus status = "AUTHENTICATED".equals(result.getStatus()) ? HttpStatus.OK
            : ("CHALLENGE_REQUIRED".equals(result.getStatus()) || "RATE_LIMITED".equals(result.getStatus()))
                ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/sessions/active")
    public Map<String, Object> active(@RequestParam("sessionId") String sessionId) {
        Map<String, Object> response=new LinkedHashMap<String,Object>();
        response.put("active", Boolean.valueOf(sessions.active(sessionId))); return response;
    }

    public static final class LoginRequest {
        private String userId; private boolean accepted;
        public String getUserId(){return userId;} public void setUserId(String userId){this.userId=userId;}
        public boolean isAccepted(){return accepted;} public void setAccepted(boolean accepted){this.accepted=accepted;}
    }
}
