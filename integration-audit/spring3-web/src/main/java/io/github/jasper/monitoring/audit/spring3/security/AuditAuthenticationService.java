package io.github.jasper.monitoring.audit.spring3.security;

import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.action.ActionRequirement;
import io.github.jasper.monitoring.api.authentication.AuthenticationMonitor;
import io.github.jasper.monitoring.api.authentication.AuthenticationStage;
import io.github.jasper.monitoring.api.authentication.LoginSubjectInput;
import io.github.jasper.monitoring.api.code.BuiltInReasonCodes;
import io.github.jasper.monitoring.audit.spring3.persistence.AuditFixtureRepository;
import java.time.Clock;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Stateful authentication host fixture using only the authentication monitoring facade. */
@Service
public final class AuditAuthenticationService {
    private final AuditFixtureRepository fixtures;
    private final AuthenticationMonitor monitoring;
    private final Clock clock = Clock.systemUTC();

    public AuditAuthenticationService(AuditFixtureRepository fixtures, AuthenticationMonitor monitoring) {
        this.fixtures = fixtures;
        this.monitoring = monitoring;
    }

    public AuthenticationResult authenticate(String loginUser, boolean accepted, Boolean captchaAccepted) {
        LoginSubjectInput subject = new LoginSubjectInput(loginUser, "audit");
        ActionDecision preCheck = monitoring.preCheck(subject);
        if (!preCheck.isAllowed()) {
            return preCheck.getControls().contains(ControlActionType.RATE_LIMIT)
                ? AuthenticationResult.rateLimited() : AuthenticationResult.denied("ACCOUNT_CONTROLLED");
        }
        if (preCheck.getRequirements().contains(ActionRequirement.CAPTCHA)) {
            if (captchaAccepted == null) return AuthenticationResult.challenge();
            if (!captchaAccepted.booleanValue()) {
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
