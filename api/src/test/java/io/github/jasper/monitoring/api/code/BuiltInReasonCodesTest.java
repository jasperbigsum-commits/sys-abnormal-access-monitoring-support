package io.github.jasper.monitoring.api.code;

import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltInReasonCodesTest {
    @Test
    void registersUniqueNamespacedBuiltInCodes() {
        StableCodeCatalog catalog = new StableCodeCatalog("ACME");
        BuiltInReasonCodes.registerInto(catalog);

        Set<String> codes = new HashSet<String>();
        for (CodeDefinition definition : catalog.asMap().values()) {
            assertTrue(definition.getCode().startsWith("MON."));
            assertTrue(codes.add(definition.getCode()));
        }
        assertEquals(17, codes.size());
    }

    @Test
    void authenticationRejectReasonsOnlyAllowDenied() {
        StableCodeCatalog catalog = new StableCodeCatalog("ACME");
        BuiltInReasonCodes.registerInto(catalog);

        CodeDefinition credential = catalog.asMap().get(
            BuiltInReasonCodes.Authentication.INVALID_CREDENTIAL.getCode());
        assertTrue(credential.getAllowedOutcomes().contains(SecurityEventResult.DENIED));
        assertFalse(credential.getAllowedOutcomes().contains(SecurityEventResult.SUCCESS));
        assertTrue(credential.appliesTo(BuiltInActions.Login.class));
    }
}
