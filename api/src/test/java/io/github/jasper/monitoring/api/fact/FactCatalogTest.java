package io.github.jasper.monitoring.api.fact;

import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
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
        assertEquals("true", BuiltInFacts.SEQUENTIAL_ACCESS.encode(" TRUE "));
        assertEquals("3.5", BuiltInFacts.BASELINE_RATIO.encode("3.5"));
        assertThrows(IllegalArgumentException.class, () -> BuiltInFacts.SEQUENTIAL_ACCESS.encode("yes"));
        assertThrows(IllegalArgumentException.class, () -> BuiltInFacts.BASELINE_RATIO.encode("NaN"));
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
