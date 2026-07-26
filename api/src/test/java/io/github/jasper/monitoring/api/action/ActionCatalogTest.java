package io.github.jasper.monitoring.api.action;

import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.fact.FactSource;
import io.github.jasper.monitoring.api.fact.FactType;
import io.github.jasper.monitoring.api.rule.RuleType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionCatalogTest {

    @Test
    void derivedExportCannotWeakenContractFailurePolicy() {
        ActionCatalog catalog = new ActionCatalog();
        catalog.registerContract(ExportContract.class, ActionContractDefinition.builder()
            .minimumFailurePolicy(ActionFailurePolicy.FAIL_CLOSED)
            .build());

        assertThrows(MonitoringConfigurationException.class,
            () -> catalog.register(FirstExport.class, action("report:export-first")
                .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY)
                .build()));
    }

    @Test
    void mergesContractAndActionRequirementsWithoutWeakeningSources() {
        ActionCatalog catalog = new ActionCatalog();
        catalog.registerContract(ExportContract.class, ActionContractDefinition.builder()
            .require(DataCountFact.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .optional(ResourceIdFact.class, FactSource.TRUSTED_REQUEST)
            .participateIn(ExportRule.class)
            .minimumFailurePolicy(ActionFailurePolicy.OBSERVE_ONLY)
            .build());
        catalog.register(FirstExport.class, action("report:export-first")
            .require(ResourceIdFact.class, FactSource.TRUSTED_REQUEST)
            .optional(DataCountFact.class, FactSource.HOST_PROVIDER)
            .participateIn(AuditRule.class)
            .failurePolicy(ActionFailurePolicy.FAIL_CLOSED)
            .build());

        ActionDefinition effective = catalog.require(FirstExport.class);

        assertEquals(ActionFailurePolicy.FAIL_CLOSED, effective.getFailurePolicy());
        assertEquals(
            new java.util.LinkedHashSet<Class<? extends FactType<?>>>(
                Arrays.<Class<? extends FactType<?>>>asList(DataCountFact.class, ResourceIdFact.class)),
            effective.getRequiredFacts());
        assertTrue(effective.getOptionalFacts().isEmpty());
        assertEquals(Collections.singleton(FactSource.HOST_PROVIDER),
            effective.getAllowedSources(DataCountFact.class));
        assertTrue(effective.getRuleTypes().containsAll(Arrays.asList(ExportRule.class, AuditRule.class)));
    }

    @Test
    void rejectsAnEmptySourceIntersection() {
        ActionCatalog catalog = new ActionCatalog();
        catalog.registerContract(ExportContract.class, ActionContractDefinition.builder()
            .require(DataCountFact.class, FactSource.METHOD_PARAMETER)
            .build());

        assertThrows(MonitoringConfigurationException.class,
            () -> catalog.register(FirstExport.class, action("report:export-first")
                .require(DataCountFact.class, FactSource.HOST_PROVIDER)
                .build()));
    }

    @Test
    void rejectsDuplicateTypeOrCodeEvenWhenDefinitionsAreEqual() {
        ActionCatalog catalog = new ActionCatalog();
        ActionDefinition first = action("report:export-first").build();
        catalog.register(PlainAction.class, first);

        assertThrows(MonitoringConfigurationException.class,
            () -> catalog.register(PlainAction.class, first));
        assertThrows(MonitoringConfigurationException.class,
            () -> catalog.register(SecondPlainAction.class, action("report:export-first").build()));
    }

    @Test
    void rejectedDuplicateContractDoesNotReplaceTheOriginal() {
        ActionCatalog catalog = new ActionCatalog();
        catalog.registerContract(ExportContract.class, ActionContractDefinition.builder()
            .minimumFailurePolicy(ActionFailurePolicy.OBSERVE_ONLY)
            .build());

        assertThrows(MonitoringConfigurationException.class,
            () -> catalog.registerContract(ExportContract.class, ActionContractDefinition.builder()
                .minimumFailurePolicy(ActionFailurePolicy.FAIL_CLOSED)
                .build()));

        catalog.register(FirstExport.class, action("report:export-first")
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY)
            .build());
        assertEquals(ActionFailurePolicy.OBSERVE_ONLY,
            catalog.require(FirstExport.class).getFailurePolicy());
    }

    @Test
    void validatesStableCodeAndConcreteFinalType() {
        ActionCatalog catalog = new ActionCatalog();

        assertThrows(IllegalArgumentException.class,
            () -> action("Report:Export").build());
        assertThrows(MonitoringConfigurationException.class,
            () -> catalog.register(NonFinalAction.class, action("report:non-final").build()));
    }

    @Test
    void freezesRegistrationAndExposesAnImmutableSnapshot() {
        ActionCatalog catalog = new ActionCatalog();
        catalog.register(PlainAction.class, action("report:plain").build());
        catalog.freeze();

        assertTrue(catalog.isFrozen());
        assertThrows(MonitoringConfigurationException.class,
            () -> catalog.register(SecondPlainAction.class, action("report:second").build()));
        assertThrows(UnsupportedOperationException.class,
            () -> catalog.asMap().clear());
        assertFalse(catalog.asMap().isEmpty());
    }

    @Test
    void repeatedFactSourceDeclarationsCannotWeakenAnEarlierConstraint() {
        assertThrows(IllegalArgumentException.class, () -> ActionContractDefinition.builder()
            .require(DataCountFact.class, FactSource.METHOD_PARAMETER)
            .require(DataCountFact.class, FactSource.HOST_PROVIDER));
    }

    @Test
    void registersBuiltInsAsTypedActionsWithoutExposingTheOwnershipMarker() {
        ActionCatalog catalog = new ActionCatalog();

        BuiltInActions.registerInto(catalog);
        catalog.freeze();

        assertEquals("report:export", catalog.require(BuiltInActions.ReportExport.class).getCode());
        assertTrue(catalog.require(BuiltInActions.ReportExport.class).getRequiredFacts()
            .contains(io.github.jasper.monitoring.api.fact.BuiltInFacts.DataCount.class));
        assertFalse(Modifier.isPublic(BuiltInActionType.class.getModifiers()));
    }

    private static ActionDefinition.Builder action(String code) {
        return ActionDefinition.builder(code)
            .eventType(SecurityEventType.EXPORT)
            .resourceType("report")
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY);
    }

    interface ExportContract extends ActionContract {
    }

    static final class FirstExport implements ActionType, ExportContract {
    }

    static final class PlainAction implements ActionType {
    }

    static final class SecondPlainAction implements ActionType {
    }

    static class NonFinalAction implements ActionType {
    }

    static final class DataCountFact implements FactType<Long> {
    }

    static final class ResourceIdFact implements FactType<String> {
    }

    static final class ExportRule implements RuleType {
    }

    static final class AuditRule implements RuleType {
    }
}
