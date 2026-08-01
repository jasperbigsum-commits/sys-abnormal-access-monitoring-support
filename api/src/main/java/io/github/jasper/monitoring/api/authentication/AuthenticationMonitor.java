package io.github.jasper.monitoring.api.authentication;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.code.ReasonCode;
import io.github.jasper.monitoring.api.event.FailureClass;

/** Authentication-specific monitoring and supplemental control boundary. */
public interface AuthenticationMonitor {
    ActionDecision preCheck(LoginSubjectInput subject);

    void recordDenied(LoginSubjectInput subject, AuthenticationStage stage, ReasonCode reason);

    void recordFailure(LoginSubjectInput subject, AuthenticationStage stage,
                       ReasonCode reason, FailureClass failureClass);

    void recordSuccess(LoginSubjectInput subject, IdentityContext authenticatedIdentity);
}
