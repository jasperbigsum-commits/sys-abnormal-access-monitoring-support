package io.github.jasper.monitoring.api.authentication;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.action.ActionDecision;
import io.github.jasper.monitoring.api.code.ReasonCode;
import io.github.jasper.monitoring.api.event.FailureClass;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticationContractTest {
    @Test
    void keepsRawLoginInputTransientAndOutOfDiagnostics() {
        LoginSubjectInput subject = new LoginSubjectInput("Alice@example.test", "tenant-a");

        assertEquals("Alice@example.test", subject.getLoginUser());
        assertEquals("tenant-a", subject.getRealm());
        assertFalse(subject.toString().contains("Alice@example.test"));
        assertThrows(IllegalArgumentException.class, () -> new LoginSubjectInput(" ", "tenant-a"));
        assertThrows(IllegalArgumentException.class, () -> new LoginSubjectInput("alice", " "));
    }

    @Test
    void facadeAcceptsDomainInputInsteadOfPrecomputedKeys() throws Exception {
        Method preCheck = AuthenticationMonitor.class.getMethod("preCheck", LoginSubjectInput.class);
        Method denied = AuthenticationMonitor.class.getMethod("recordDenied", LoginSubjectInput.class,
            AuthenticationStage.class, ReasonCode.class);
        Method failure = AuthenticationMonitor.class.getMethod("recordFailure", LoginSubjectInput.class,
            AuthenticationStage.class, ReasonCode.class, FailureClass.class);
        Method success = AuthenticationMonitor.class.getMethod("recordSuccess", LoginSubjectInput.class,
            IdentityContext.class);

        assertEquals(ActionDecision.class, preCheck.getReturnType());
        assertEquals(Void.TYPE, denied.getReturnType());
        assertEquals(Void.TYPE, failure.getReturnType());
        assertEquals(Void.TYPE, success.getReturnType());
    }
}
