package io.github.jasper.monitoring.api.fact;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

interface BuiltInFactType<T> extends FactType<T> {
}

/** Standard fact tokens and their canonical runtime definitions. */
public final class BuiltInFacts {
    public static final FactDefinition<String> RESOURCE_ID = FactDefinition
        .builder(ResourceId.class, "resource_id", String.class)
        .allowedSources(FactSource.TRUSTED_REQUEST, FactSource.METHOD_PARAMETER,
            FactSource.HOST_PROVIDER, FactSource.CLIENT_SUPPLEMENTAL)
        .sensitivity(FactDefinition.Sensitivity.INTERNAL)
        .maxLength(256)
        .storage(FactDefinition.Storage.STANDARD_COLUMN)
        .codec(FactDefinition.stringCodec(value -> value.trim()))
        .build();

    public static final FactDefinition<Long> DATA_COUNT = FactDefinition
        .builder(DataCount.class, "data_count", Long.class)
        .allowedSources(FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
        .sensitivity(FactDefinition.Sensitivity.INTERNAL)
        .maxLength(20)
        .storage(FactDefinition.Storage.EXTENSION)
        .codec(FactDefinition.longCodec(value -> value))
        .validator(value -> value >= 0L)
        .build();

    public static final FactDefinition<String> SENSITIVITY = FactDefinition
        .builder(Sensitivity.class, "sensitivity", String.class)
        .allowedSources(FactSource.TRUSTED_REQUEST, FactSource.HOST_PROVIDER)
        .sensitivity(FactDefinition.Sensitivity.INTERNAL)
        .maxLength(64)
        .storage(FactDefinition.Storage.EXTENSION)
        .codec(FactDefinition.stringCodec(
            value -> value.trim().toLowerCase(java.util.Locale.ROOT)))
        .build();

    private static final List<FactDefinition<?>> ALL = Collections.unmodifiableList(
        Arrays.<FactDefinition<?>>asList(RESOURCE_ID, DATA_COUNT, SENSITIVITY));

    private BuiltInFacts() {
    }

    /** @return every built-in fact definition in stable registration order */
    public static List<FactDefinition<?>> all() {
        return ALL;
    }

    /** Registers every library-owned fact definition into a mutable catalog. */
    public static void registerInto(FactCatalog catalog) {
        for (FactDefinition<?> definition : ALL) {
            catalog.register(definition);
        }
    }

    /** Built-in resource identifier token. */
    public static final class ResourceId implements BuiltInFactType<String> {
        private ResourceId() {
        }
    }

    /** Built-in operation data count token. */
    public static final class DataCount implements BuiltInFactType<Long> {
        private DataCount() {
        }
    }

    /** Built-in logical sensitivity token. */
    public static final class Sensitivity implements BuiltInFactType<String> {
        private Sensitivity() {
        }
    }
}
