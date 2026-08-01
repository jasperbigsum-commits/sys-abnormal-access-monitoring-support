package io.github.jasper.monitoring.audit.spring2.security;

import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.api.code.ReasonCode;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.api.event.FailureClass;
import io.github.jasper.monitoring.audit.spring2.persistence.AuditFixtureRepository;
import io.github.jasper.monitoring.core.application.MonitoringService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Collections;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 用于 TC-01 与 TC-03 的有状态认证宿主边界。
 *
 * <p>这是集成夹具实现：生产系统应复用“认证决策后提交服务端事件”的边界模式，
 * 但必须替换夹具账号、会话和认证结果来源。</p>
 */
@Service
public final class AuditAuthenticationService {
    // 集成夹具实现：使用 audit_* 宿主测试表模拟账号、控制状态和会话。
    private final AuditFixtureRepository fixtures;
    private final MonitoringService monitoring;
    private final Clock clock = Clock.systemUTC();

    public AuditAuthenticationService(AuditFixtureRepository fixtures, MonitoringService monitoring) {
        this.fixtures = fixtures;
        this.monitoring = monitoring;
    }

    public AuthenticationResult authenticate(String userId, boolean accepted, String clientIp) {
        // 集成夹具实现：从固定测试账号表读取认证状态。
        Map<String, Object> account = fixtures.findAccount(userId);
        if (account.isEmpty()) {
            return AuthenticationResult.denied("UNKNOWN_ACCOUNT");
        }
        // 集成夹具实现：验证码、限流和拒绝状态由 audit_control_state 模拟。
        if (fixtures.hasActiveControl("ip:" + clientIp, "RATE_LIMIT", clock.instant())) {
            return AuthenticationResult.rateLimited();
        }
        if (fixtures.hasActiveControl(userId, "DENY", clock.instant())) {
            return AuthenticationResult.denied("ACCOUNT_CONTROLLED");
        }
        if (fixtures.hasActiveControl(userId, "REQUIRE_CAPTCHA", clock.instant())) {
            return AuthenticationResult.challenge();
        }
        if (!"ACTIVE".equals(String.valueOf(account.get("STATUS")))) {
            recordFailure(userId, clientIp, "ACCOUNT_DISABLED");
            return AuthenticationResult.denied("ACCOUNT_DISABLED");
        }
        if (!accepted) {
            fixtures.incrementFailedLogins(userId);
            recordFailure(userId, clientIp, "INVALID_CREDENTIAL");
            return AuthenticationResult.denied("INVALID_CREDENTIAL");
        }
        // 集成夹具实现：成功认证后写入测试会话表；生产系统应委托真实会话服务。
        String sessionId = UUID.randomUUID().toString();
        fixtures.createSession(sessionId, userId, clock.instant());
        return AuthenticationResult.authenticated(sessionId);
    }

    private void recordFailure(String userId, String clientIp, String reason) {
        // 集成夹具实现：构造最小请求上下文以验证登录失败事件；生产中应复用当前请求可信上下文。
        MonitoringRequestContext request = MonitoringRequestContext.builder().method("POST")
            .path("/audit/authentication/login").sourceIp(clientIp)
            .requestId(UUID.randomUUID().toString()).build();
        monitoring.monitor(ActionExecution.of(BuiltInActions.Login.class, request,
            new IdentityContext(userId, AccountType.PERSON, Collections.singleton(userId), null), ActionOutcome.failure(
                reasonCode(reason), FailureClass.AUTHORIZATION, 0L)));
    }

    private static ReasonCode reasonCode(String reason) {
        if ("ACCOUNT_DISABLED".equals(reason)) return BuiltInReasonCodes.Authentication.ACCOUNT_DISABLED;
        return BuiltInReasonCodes.Authentication.INVALID_CREDENTIAL;
    }

    public static final class AuthenticationResult {
        private final String status; private final String reason; private final String sessionId;
        private AuthenticationResult(String status, String reason, String sessionId) {
            this.status=status; this.reason=reason; this.sessionId=sessionId;
        }
        static AuthenticationResult authenticated(String sessionId) { return new AuthenticationResult("AUTHENTICATED", null, sessionId); }
        static AuthenticationResult denied(String reason) { return new AuthenticationResult("DENIED", reason, null); }
        static AuthenticationResult challenge() { return new AuthenticationResult("CHALLENGE_REQUIRED", null, null); }
        static AuthenticationResult rateLimited() { return new AuthenticationResult("RATE_LIMITED", null, null); }
        public String getStatus() { return status; }
        public String getReason() { return reason; }
        public String getSessionId() { return sessionId; }
    }
}
