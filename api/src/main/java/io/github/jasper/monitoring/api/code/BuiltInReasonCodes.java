package io.github.jasper.monitoring.api.code;

import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.action.BuiltInActions;

/** Framework-owned outcome reasons and their action/result scopes. */
public final class BuiltInReasonCodes {
    private BuiltInReasonCodes() {
    }

    public enum Authentication implements ReasonCode {
        INVALID_CREDENTIAL("MON.AUTH.INVALID_CREDENTIAL"),
        CAPTCHA_REQUIRED("MON.AUTH.CAPTCHA_REQUIRED"),
        CAPTCHA_INVALID("MON.AUTH.CAPTCHA_INVALID"),
        CAPTCHA_EXPIRED("MON.AUTH.CAPTCHA_EXPIRED"),
        MFA_INVALID("MON.AUTH.MFA_INVALID"),
        ACCOUNT_DISABLED("MON.AUTH.ACCOUNT_DISABLED"),
        ACCOUNT_LOCKED("MON.AUTH.ACCOUNT_LOCKED"),
        RATE_LIMITED("MON.AUTH.RATE_LIMITED"),
        AUTHENTICATION_UNAVAILABLE("MON.AUTH.AUTHENTICATION_UNAVAILABLE");

        private final String code;
        Authentication(String code) { this.code = code; }
        @Override public String getCode() { return code; }
        @Override public CodeFamily getFamily() { return CodeFamily.OUTCOME_REASON; }
    }

    public enum Authorization implements ReasonCode {
        RESOURCE_SCOPE_DENIED("MON.AUTHZ.RESOURCE_SCOPE_DENIED"),
        NO_DECISION("MON.AUTHZ.NO_DECISION"),
        EVALUATION_ERROR("MON.AUTHZ.EVALUATION_ERROR"),
        AUTHORIZER_NOT_CONFIGURED("MON.AUTHZ.AUTHORIZER_NOT_CONFIGURED");

        private final String code;
        Authorization(String code) { this.code = code; }
        @Override public String getCode() { return code; }
        @Override public CodeFamily getFamily() { return CodeFamily.OUTCOME_REASON; }
    }

    public enum Action implements ReasonCode {
        BLOCKED("MON.ACTION.BLOCKED"),
        INVOCATION_FAILED("MON.ACTION.INVOCATION_FAILED"),
        REQUEST_FAILED("MON.ACTION.REQUEST_FAILED");

        private final String code;
        Action(String code) { this.code = code; }
        @Override public String getCode() { return code; }
        @Override public CodeFamily getFamily() { return CodeFamily.OUTCOME_REASON; }
    }

    public enum Privilege implements ReasonCode {
        SELF_ESCALATION("MON.PRIVILEGE.SELF_ESCALATION");

        private final String code;
        Privilege(String code) { this.code = code; }
        @Override public String getCode() { return code; }
        @Override public CodeFamily getFamily() { return CodeFamily.OUTCOME_REASON; }
    }

    public static void registerInto(StableCodeCatalog catalog) {
        for (Authentication code : Authentication.values()) {
            outcomes(catalog, code, code == Authentication.AUTHENTICATION_UNAVAILABLE
                ? SecurityEventResult.FAILURE : SecurityEventResult.DENIED,
                BuiltInActions.LoginFailure.class);
        }
        for (Authorization code : Authorization.values()) {
            outcomes(catalog, code,
                code == Authorization.EVALUATION_ERROR
                    ? SecurityEventResult.FAILURE : SecurityEventResult.DENIED,
                BuiltInActions.AccessDenied.class);
        }
        outcomes(catalog, Action.BLOCKED, SecurityEventResult.DENIED,
            ActionType.class);
        outcomes(catalog, Action.INVOCATION_FAILED, SecurityEventResult.FAILURE,
            ActionType.class);
        outcomes(catalog, Action.REQUEST_FAILED, SecurityEventResult.FAILURE,
            ActionType.class);
        outcomes(catalog, Privilege.SELF_ESCALATION, SecurityEventResult.DENIED,
            BuiltInActions.PrivilegeChange.class);
    }

    @SafeVarargs
    private static void outcomes(StableCodeCatalog catalog, ReasonCode code,
            SecurityEventResult outcome, Class<? extends ActionType>... actions) {
        catalog.registerBuiltIn(CodeDefinition.reason(code).allow(outcome).appliesTo(actions).build());
    }
}
