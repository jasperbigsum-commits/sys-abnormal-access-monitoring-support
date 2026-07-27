package io.github.jasper.monitoring.api.action;

import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactSource;

interface BuiltInActionType extends ActionType {
}

/** Public built-in action tokens with library-owned registration metadata. */
public final class BuiltInActions {
    private BuiltInActions() {
    }

    /** Registers the standard contracts and actions into a mutable catalog. */
    public static void registerInto(ActionCatalog catalog) {
        catalog.registerContract(ExportContract.class, ActionContractDefinition.builder()
            .require(BuiltInFacts.ResourceId.class,
                FactSource.TRUSTED_REQUEST, FactSource.HOST_PROVIDER)
            .require(BuiltInFacts.DataCount.class,
                FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .minimumFailurePolicy(ActionFailurePolicy.FAIL_CLOSED)
            .build());
        catalog.register(ReportExport.class, ActionDefinition.builder("report:export")
            .eventType(SecurityEventType.EXPORT)
            .resourceType("report")
            .ruleTag("export")
            .optional(BuiltInFacts.Sensitivity.class, FactSource.TRUSTED_REQUEST, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.BaselineRatio.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.FAIL_CLOSED)
            .build());
        catalog.register(LoginFailure.class, action("auth:login-failure", SecurityEventType.LOGIN_FAILURE));
        catalog.register(Query.class, ActionDefinition.builder("data:query")
            .eventType(SecurityEventType.QUERY).resourceType("resource")
            .optional(BuiltInFacts.ResourceId.class, FactSource.TRUSTED_REQUEST,
                FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.SequentialAccess.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build());
        catalog.register(SessionConcurrent.class, ActionDefinition.builder("session:concurrent")
            .eventType(SecurityEventType.SESSION_CONCURRENT).resourceType("monitoring")
            .optional(BuiltInFacts.DataCount.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.DifferentNetworks.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build());
        catalog.register(AccessDenied.class, access("authz:access-denied", SecurityEventType.ACCESS_DENIED));
        catalog.register(AccessAllowed.class, access("authz:access-allowed", SecurityEventType.ACCESS_ALLOWED));
        catalog.register(PrivilegeChange.class, ActionDefinition.builder("privilege:change")
            .eventType(SecurityEventType.ROLE_GRANT).resourceType("monitoring")
            .require(BuiltInFacts.TargetUserId.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .require(BuiltInFacts.PrivilegeIncrease.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.HighPrivilege.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.FAIL_CLOSED).build());
        catalog.register(SecurityChange.class, action("security:configuration-change", SecurityEventType.RULE_CHANGE));
        catalog.register(SensitiveView.class, ActionDefinition.builder("resource:view-sensitive")
            .eventType(SecurityEventType.VIEW_SENSITIVE)
            .resourceType("resource")
            .ruleTag("sensitive")
            .optional(BuiltInFacts.Sensitivity.class,
                FactSource.TRUSTED_REQUEST, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.ResourceId.class, FactSource.TRUSTED_REQUEST,
                FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.DataCount.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.Sensitive.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.WorkHours.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY)
            .build());
        catalog.register(FrontendSignal.class, ActionDefinition.builder("frontend:signal")
            .eventType(SecurityEventType.QUERY).resourceType("frontend-route")
            .optional(BuiltInFacts.ResourceId.class, FactSource.CLIENT_SUPPLEMENTAL)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build());
    }

    private static ActionDefinition action(String code, SecurityEventType eventType) {
        return ActionDefinition.builder(code).eventType(eventType).resourceType("monitoring")
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
    }

    private static ActionDefinition access(String code, SecurityEventType eventType) {
        return ActionDefinition.builder(code).eventType(eventType).resourceType("resource")
            .optional(BuiltInFacts.ResourceId.class, FactSource.TRUSTED_REQUEST, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
    }

    /** Public semantic contract shared by all export actions. */
    public interface ExportContract extends ActionContract {
    }

    /** Built-in report export action. */
    public static final class ReportExport implements BuiltInActionType, ExportContract {
        private ReportExport() {
        }
    }

    /** Built-in sensitive resource view action. */
    public static final class SensitiveView implements BuiltInActionType {
        private SensitiveView() {
        }
    }
    public static final class LoginFailure implements BuiltInActionType { private LoginFailure() { } }
    public static final class Query implements BuiltInActionType { private Query() { } }
    public static final class SessionConcurrent implements BuiltInActionType { private SessionConcurrent() { } }
    public static final class AccessDenied implements BuiltInActionType { private AccessDenied() { } }
    public static final class AccessAllowed implements BuiltInActionType { private AccessAllowed() { } }
    public static final class PrivilegeChange implements BuiltInActionType { private PrivilegeChange() { } }
    public static final class SecurityChange implements BuiltInActionType { private SecurityChange() { } }
    /** Supplemental browser telemetry; deliberately not bound to any built-in rule. */
    public static final class FrontendSignal implements BuiltInActionType { private FrontendSignal() { } }
}
