package io.github.jasper.monitoring.api.code;

import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.action.ActionType;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StableCodeCatalogTest {
    private static final class OrderAction implements ActionType { }
    private static final class QueryAction implements ActionType { }

    private enum CreditRejected implements ReasonCode {
        INSTANCE;

        @Override public String getCode() { return "ACME.ORDER.CREDIT_REJECTED"; }
        @Override public CodeFamily getFamily() { return CodeFamily.OUTCOME_REASON; }
    }

    private enum BuiltInReason implements ReasonCode {
        INSTANCE;

        @Override public String getCode() { return "MON.AUTH.INVALID_CREDENTIAL"; }
        @Override public CodeFamily getFamily() { return CodeFamily.OUTCOME_REASON; }
    }

    @Test
    void validatesRegisteredReasonAgainstActionAndOutcome() {
        StableCodeCatalog catalog = new StableCodeCatalog("ACME");
        catalog.registerHost(CodeDefinition.reason(CreditRejected.INSTANCE)
            .allow(SecurityEventResult.DENIED)
            .appliesTo(OrderAction.class)
            .build());
        catalog.freeze();

        assertDoesNotThrow(() -> catalog.validateReason(CreditRejected.INSTANCE,
            OrderAction.class, SecurityEventResult.DENIED));
        assertThrows(MonitoringConfigurationException.class, () ->
            catalog.validateReason(CreditRejected.INSTANCE, QueryAction.class,
                SecurityEventResult.DENIED));
        assertThrows(MonitoringConfigurationException.class, () ->
            catalog.validateReason(CreditRejected.INSTANCE, OrderAction.class,
                SecurityEventResult.SUCCESS));
    }

    @Test
    void rejectsHostRegistrationOfFrameworkNamespaceAndFreezes() {
        StableCodeCatalog catalog = new StableCodeCatalog("ACME");
        assertThrows(MonitoringConfigurationException.class, () ->
            catalog.registerHost(CodeDefinition.reason(BuiltInReason.INSTANCE)
                .allow(SecurityEventResult.DENIED)
                .appliesTo(OrderAction.class)
                .build()));

        catalog.registerBuiltIn(CodeDefinition.reason(BuiltInReason.INSTANCE)
            .allow(SecurityEventResult.DENIED)
            .appliesTo(OrderAction.class)
            .build());
        catalog.freeze();
        assertThrows(MonitoringConfigurationException.class, () ->
            catalog.registerBuiltIn(CodeDefinition.reason(BuiltInReason.INSTANCE)
                .allow(SecurityEventResult.DENIED)
                .appliesTo(QueryAction.class)
                .build()));
    }

    @Test
    void rejectsMalformedAndDuplicateDefinitions() {
        ReasonCode malformed = new ReasonCode() {
            @Override public String getCode() { return "ACME.order.dynamic-value"; }
            @Override public CodeFamily getFamily() { return CodeFamily.OUTCOME_REASON; }
        };
        StableCodeCatalog catalog = new StableCodeCatalog("ACME");
        assertThrows(IllegalArgumentException.class, () ->
            catalog.registerHost(CodeDefinition.reason(malformed)
                .allow(SecurityEventResult.DENIED)
                .appliesTo(OrderAction.class)
                .build()));

        catalog.registerHost(CodeDefinition.reason(CreditRejected.INSTANCE)
            .allow(SecurityEventResult.DENIED)
            .appliesTo(OrderAction.class)
            .build());
        assertThrows(MonitoringConfigurationException.class, () ->
            catalog.registerHost(CodeDefinition.reason(CreditRejected.INSTANCE)
                .allow(SecurityEventResult.DENIED)
                .appliesTo(OrderAction.class)
                .build()));
    }
}
