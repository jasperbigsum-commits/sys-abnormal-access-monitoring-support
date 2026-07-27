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

    public static final FactDefinition<String> DIFFERENT_NETWORKS = booleanFact(
        DifferentNetworks.class, "different_networks");
    public static final FactDefinition<String> SEQUENTIAL_ACCESS = booleanFact(
        SequentialAccess.class, "sequential_access");
    public static final FactDefinition<String> SENSITIVE = booleanFact(Sensitive.class, "sensitive");
    public static final FactDefinition<String> WORK_HOURS = booleanFact(WorkHours.class, "work_hours");
    public static final FactDefinition<String> PRIVILEGE_INCREASE = booleanFact(
        PrivilegeIncrease.class, "privilege_increase");
    public static final FactDefinition<String> HIGH_PRIVILEGE = booleanFact(HighPrivilege.class, "high_privilege");
    public static final FactDefinition<String> TARGET_USER_ID = FactDefinition
        .builder(TargetUserId.class, "target_user_id", String.class)
        .allowedSources(FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
        .sensitivity(FactDefinition.Sensitivity.INTERNAL).maxLength(128)
        .storage(FactDefinition.Storage.EXTENSION)
        .codec(FactDefinition.stringCodec(value -> value.trim())).build();
    public static final FactDefinition<String> BASELINE_RATIO = FactDefinition
        .builder(BaselineRatio.class, "baseline_ratio", String.class)
        .allowedSources(FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
        .sensitivity(FactDefinition.Sensitivity.INTERNAL).maxLength(32)
        .storage(FactDefinition.Storage.EXTENSION)
        .codec(FactDefinition.stringCodec(value -> value.trim()))
        .validator(BuiltInFacts::validNonNegativeNumber).build();

    private static final List<FactDefinition<?>> ALL = Collections.unmodifiableList(
        Arrays.<FactDefinition<?>>asList(RESOURCE_ID, DATA_COUNT, SENSITIVITY, DIFFERENT_NETWORKS,
            SEQUENTIAL_ACCESS, SENSITIVE, WORK_HOURS, PRIVILEGE_INCREASE, HIGH_PRIVILEGE,
            TARGET_USER_ID, BASELINE_RATIO));

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
    public static final class DifferentNetworks implements BuiltInFactType<String> { private DifferentNetworks() { } }
    public static final class SequentialAccess implements BuiltInFactType<String> { private SequentialAccess() { } }
    public static final class Sensitive implements BuiltInFactType<String> { private Sensitive() { } }
    public static final class WorkHours implements BuiltInFactType<String> { private WorkHours() { } }
    public static final class PrivilegeIncrease implements BuiltInFactType<String> { private PrivilegeIncrease() { } }
    public static final class HighPrivilege implements BuiltInFactType<String> { private HighPrivilege() { } }
    public static final class TargetUserId implements BuiltInFactType<String> { private TargetUserId() { } }
    public static final class BaselineRatio implements BuiltInFactType<String> { private BaselineRatio() { } }

    private static <F extends FactType<String>> FactDefinition<String> booleanFact(Class<F> type, String key) {
        return FactDefinition.builder(type, key, String.class)
            .allowedSources(FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .sensitivity(FactDefinition.Sensitivity.INTERNAL).maxLength(5)
            .storage(FactDefinition.Storage.EXTENSION)
            .codec(FactDefinition.stringCodec(value -> value.trim().toLowerCase(java.util.Locale.ROOT)))
            .validator(value -> "true".equals(value) || "false".equals(value)).build();
    }

    private static boolean validNonNegativeNumber(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return !Double.isNaN(parsed) && !Double.isInfinite(parsed) && parsed >= 0.0d;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
