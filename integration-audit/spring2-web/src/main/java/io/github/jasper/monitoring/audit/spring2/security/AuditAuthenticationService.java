package io.github.jasper.monitoring.audit.spring2.security;

import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.AccountType;
import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.event.ActionExecution;
import io.github.jasper.monitoring.api.event.ActionOutcome;
import io.github.jasper.monitoring.audit.spring2.persistence.AuditFixtureRepository;
import io.github.jasper.monitoring.core.application.MonitoringService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Collections;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Stateful host authentication boundary used by TC-01 and TC-03. */
@Service
public final class AuditAuthenticationService {
    private final AuditFixtureRepository fixtures;
    private final MonitoringService monitoring;
    private final Clock clock = Clock.systemUTC();

    public AuditAuthenticationService(AuditFixtureRepository fixtures, MonitoringService monitoring) {
        this.fixtures = fixtures;
        this.monitoring = monitoring;
    }

    public AuthenticationResult authenticate(String userId, boolean accepted, String clientIp) {
        Map<String, Object> account = fixtures.findAccount(userId);
        if (account.isEmpty()) {
            return AuthenticationResult.denied("UNKNOWN_ACCOUNT");
        }
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
        String sessionId = UUID.randomUUID().toString();
        fixtures.createSession(sessionId, userId, clock.instant());
        return AuthenticationResult.authenticated(sessionId);
    }

    private void recordFailure(String userId, String clientIp, String reason) {
        MonitoringRequestContext request = MonitoringRequestContext.builder().method("POST")
            .path("/audit/authentication/login").sourceIp(clientIp)
            .requestId(UUID.randomUUID().toString()).build();
        monitoring.monitor(ActionExecution.of(BuiltInActions.LoginFailure.class, request,
            new IdentityContext(userId, AccountType.PERSON, Collections.singleton(userId), null), ActionOutcome.failure(
                reason, ActionOutcome.ExceptionClassification.AUTHORIZATION, 0L)));
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
