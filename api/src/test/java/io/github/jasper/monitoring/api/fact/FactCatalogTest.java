package io.github.jasper.monitoring.api.fact;

import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FactCatalogTest {
    @Test
    void freezesUniqueFactTypesAndKeys() {
        FactCatalog catalog = new FactCatalog();
        BuiltInFacts.registerInto(catalog);
        catalog.freeze();

        assertEquals(BuiltInFacts.DATA_COUNT, catalog.require(BuiltInFacts.DataCount.class));
        assertEquals("high", BuiltInFacts.SENSITIVITY.encode(BuiltInFacts.SensitivityLevel.HIGH));
        assertEquals("true", BuiltInFacts.SEQUENTIAL_ACCESS.encode(Boolean.TRUE));
        assertEquals("3.5", BuiltInFacts.BASELINE_RATIO.encode(new BigDecimal("3.50")));
        assertEquals(BuiltInFacts.SensitivityLevel.HIGH, BuiltInFacts.SENSITIVITY.decode("HIGH"));
        assertEquals(Boolean.FALSE, BuiltInFacts.SEQUENTIAL_ACCESS.decode("false"));
        assertEquals(new BigDecimal("3.5"), BuiltInFacts.BASELINE_RATIO.decode("3.50"));
        assertThrows(IllegalArgumentException.class, () -> BuiltInFacts.SEQUENTIAL_ACCESS.decode("yes"));
        assertThrows(IllegalArgumentException.class, () -> BuiltInFacts.BASELINE_RATIO.decode("NaN"));
        assertThrows(IllegalArgumentException.class, () -> BuiltInFacts.BASELINE_RATIO.encode(new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,
            () -> BuiltInFacts.SENSITIVITY.encodeRaw("high"));
        assertThrows(MonitoringConfigurationException.class,
            () -> catalog.register(BuiltInFacts.DATA_COUNT));
    }

    @Test
    void rejectsDuplicatePersistenceKeys() {
        FactCatalog catalog = new FactCatalog();
        catalog.register(definition(First.class));

        assertThrows(MonitoringConfigurationException.class,
            () -> catalog.register(definition(Second.class)));
    }

    private static FactDefinition<String> definition(Class<? extends FactType<String>> type) {
        return FactDefinition.builder(type, "same_key", String.class)
            .allowedSources(FactSource.HOST_PROVIDER)
            .sensitivity(FactDefinition.Sensitivity.INTERNAL)
            .maxLength(32)
            .storage(FactDefinition.Storage.EXTENSION)
            .codec(FactDefinition.stringCodec(value -> value))
            .build();
    }

    static final class First implements FactType<String> { }
    static final class Second implements FactType<String> { }
}
