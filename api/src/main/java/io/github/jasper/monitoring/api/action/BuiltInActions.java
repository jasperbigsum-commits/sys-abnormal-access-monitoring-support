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
            .failurePolicy(ActionFailurePolicy.FAIL_CLOSED)
            .build());
        catalog.register(SensitiveView.class, ActionDefinition.builder("resource:view-sensitive")
            .eventType(SecurityEventType.VIEW_SENSITIVE)
            .resourceType("resource")
            .ruleTag("sensitive")
            .optional(BuiltInFacts.Sensitivity.class,
                FactSource.TRUSTED_REQUEST, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY)
            .build());
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
}
