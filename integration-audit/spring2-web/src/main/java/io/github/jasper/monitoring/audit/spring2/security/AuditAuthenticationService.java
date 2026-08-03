package io.github.jasper.monitoring.audit.spring2.security;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.action.ActionRequirement;
import io.github.jasper.monitoring.api.authentication.AuthenticationMonitor;
import io.github.jasper.monitoring.api.authentication.AuthenticationStage;
import io.github.jasper.monitoring.api.authentication.LoginSubjectInput;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.audit.spring2.persistence.AuditFixtureRepository;
import java.time.Clock;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 仅通过认证监测门面接入组件的有状态宿主认证夹具。
 *
 * <p>该类用于验收控制预检、验证码挑战、凭据拒绝和成功身份记录的调用顺序，
 * 不是可直接复制到生产的认证实现。</p>
 */
@Service
public final class AuditAuthenticationService {
    private final AuditFixtureRepository fixtures;
    private final AuthenticationMonitor monitoring;
    private final Clock clock = Clock.systemUTC();

    /**
     * 创建验收宿主认证服务。
     *
     * @param fixtures 夹具业务状态仓储
     * @param monitoring 认证监测门面
     */
    public AuditAuthenticationService(AuditFixtureRepository fixtures, AuthenticationMonitor monitoring) {
        this.fixtures = fixtures;
        this.monitoring = monitoring;
    }

    /**
     * 执行一次夹具认证并在真实决策分支记录监测结果。
     *
     * @param loginUser 登录标识
     * @param accepted 是否通过主凭据校验
     * @param captchaAccepted 验证码结果；{@code null} 表示尚未提交验证码
     * @return 夹具认证结果
     */
    public AuthenticationResult authenticate(String loginUser, boolean accepted, Boolean captchaAccepted) {
        LoginSubjectInput subject = new LoginSubjectInput(loginUser, "audit");
        ActionDecision preCheck = monitoring.preCheck(subject);
        if (!preCheck.isAllowed()) {
            return preCheck.getControls().contains(ControlActionType.RATE_LIMIT)
                ? AuthenticationResult.rateLimited() : AuthenticationResult.denied("ACCOUNT_CONTROLLED");
        }
        if (preCheck.getRequirements().contains(ActionRequirement.CAPTCHA)) {
            if (captchaAccepted == null) return AuthenticationResult.challenge();
            if (!captchaAccepted) {
                monitoring.recordDenied(subject, AuthenticationStage.CAPTCHA,
                    BuiltInReasonCodes.Authentication.CAPTCHA_INVALID);
                return AuthenticationResult.denied("CAPTCHA_INVALID");
            }
        }
        if (preCheck.getRequirements().contains(ActionRequirement.MFA)) {
            return AuthenticationResult.challenge();
        }

        Map<String, Object> account = fixtures.findAccount(loginUser);
        if (account.isEmpty()) {
            monitoring.recordDenied(subject, AuthenticationStage.CREDENTIAL,
                BuiltInReasonCodes.Authentication.INVALID_CREDENTIAL);
            return AuthenticationResult.denied("INVALID_CREDENTIAL");
        }
        if (!"ACTIVE".equals(String.valueOf(account.get("STATUS")))) {
            monitoring.recordDenied(subject, AuthenticationStage.CREDENTIAL,
                BuiltInReasonCodes.Authentication.ACCOUNT_DISABLED);
            return AuthenticationResult.denied("ACCOUNT_DISABLED");
        }
        if (!accepted) {
            fixtures.incrementFailedLogins(loginUser);
            monitoring.recordDenied(subject, AuthenticationStage.CREDENTIAL,
                BuiltInReasonCodes.Authentication.INVALID_CREDENTIAL);
            return AuthenticationResult.denied("INVALID_CREDENTIAL");
        }
        String sessionId = UUID.randomUUID().toString();
        fixtures.createSession(sessionId, loginUser, clock.instant());
        monitoring.recordSuccess(subject, new IdentityContext(loginUser, AccountType.PERSON,
            Collections.<String>emptySet(), null));
        return AuthenticationResult.authenticated(sessionId);
    }

    /** 供验收 Controller 返回的最小认证结果。 */
    public static final class AuthenticationResult {
        private final String status; private final String reason; private final String sessionId;
        private AuthenticationResult(String status, String reason, String sessionId) {
            this.status=status; this.reason=reason; this.sessionId=sessionId;
        }
        static AuthenticationResult authenticated(String sessionId) { return new AuthenticationResult("AUTHENTICATED", null, sessionId); }
        static AuthenticationResult denied(String reason) { return new AuthenticationResult("DENIED", reason, null); }
        static AuthenticationResult challenge() { return new AuthenticationResult("CHALLENGE_REQUIRED", null, null); }
        static AuthenticationResult rateLimited() { return new AuthenticationResult("RATE_LIMITED", null, null); }
        /** @return 认证状态 */
        public String getStatus() { return status; }
        /** @return 稳定拒绝原因；没有拒绝时为 {@code null} */
        public String getReason() { return reason; }
        /** @return 成功创建的会话标识；未认证成功时为 {@code null} */
        public String getSessionId() { return sessionId; }
    }
}
