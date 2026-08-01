package io.github.jasper.monitoring.audit.spring2.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 参考宿主的匿名登录与会话探针 Controller。
 *
 * <p>这是集成夹具实现：路由、请求 DTO 和响应状态只服务验收，不是生产认证 API 规范。</p>
 */
@RestController
@RequestMapping("/audit/authentication")
public class AuthenticationController {
    private final AuditAuthenticationService authentication; private final AuditSessionService sessions;
    public AuthenticationController(AuditAuthenticationService authentication, AuditSessionService sessions) {
        this.authentication=authentication; this.sessions=sessions;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest body) {
        // 集成夹具实现：accepted 只模拟认证结果；生产不得接受客户端提交的认证结论。
        AuditAuthenticationService.AuthenticationResult result = authentication.authenticate(
            body.getUserId(), body.isAccepted(), body.getCaptchaAccepted());
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
        // 集成夹具实现：暴露会话探针以验证撤销副作用；生产应限制或移除此类端点。
        Map<String, Object> response=new LinkedHashMap<String,Object>();
        response.put("active", Boolean.valueOf(sessions.active(sessionId))); return response;
    }

    public static final class LoginRequest {
        // 集成夹具实现：该 DTO 只承载测试输入，不代表生产登录凭据模型。
        private String userId; private boolean accepted; private Boolean captchaAccepted;
        public String getUserId(){return userId;} public void setUserId(String userId){this.userId=userId;}
        public boolean isAccepted(){return accepted;} public void setAccepted(boolean accepted){this.accepted=accepted;}
        public Boolean getCaptchaAccepted(){return captchaAccepted;}
        public void setCaptchaAccepted(Boolean captchaAccepted){this.captchaAccepted=captchaAccepted;}
    }
}
