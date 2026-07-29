package io.github.jasper.monitoring.api.fact;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Fact 的唯一运行时规范定义。
 * <p>
 * 该对象把一个 Fact 的“全套治理元数据”固定下来：key、值类型、允许来源、敏感级别、
 * 长度限制、存储策略、归一化/编解码与合法性校验。
 */
public final class FactDefinition<T> {
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_.-]{0,127}");

    /** 敏感级别分类（用于脱敏和可见性控制）。 */
    public enum Sensitivity {
        /** 可公开展示：如非敏感统计标签。 */
        PUBLIC,
        /** 仅内部可见：默认业务与运维诊断字段。 */
        INTERNAL,
        /** 高敏数据：需要更严格脱敏与授权控制。 */
        SENSITIVE
    }

    /** 持久化策略分类（决定落库位置）。 */
    public enum Storage {
        /** 标准列：高频、结构稳定、需要快速检索/筛选的事实。 */
        STANDARD_COLUMN,
        /** 扩展区：低频或可扩展事实，通常以扩展字段形式存储。 */
        EXTENSION
    }

    /** Converts a fact between its typed value and stable persistence representation. */
    public interface Codec<T> {
        T normalize(T value);

        String encode(T value);

        T decode(String encoded);
    }

    private final Class<? extends FactType<T>> factType;
    private final String key;
    private final Class<T> valueType;
    private final Codec<T> codec;
    private final Set<FactSource> allowedSources;
    private final Sensitivity sensitivity;
    private final int maxLength;
    private final Predicate<T> validator;
    private final Storage storage;

    private FactDefinition(Builder<T> builder) {
        this.factType = builder.factType;
        this.key = builder.key;
        this.valueType = builder.valueType;
        this.codec = builder.codec;
        this.allowedSources = Collections.unmodifiableSet(EnumSet.copyOf(builder.allowedSources));
        this.sensitivity = builder.sensitivity;
        this.maxLength = builder.maxLength;
        this.validator = builder.validator;
        this.storage = builder.storage;
    }

    /** Creates a builder that preserves the fact token's compile-time value type. */
    public static <T> Builder<T> builder(Class<? extends FactType<T>> factType,
            String key, Class<T> valueType) {
        return new Builder<T>(factType, key, valueType);
    }

    /** Creates the standard decimal long codec with a caller-supplied normalizer. */
    public static Codec<Long> longCodec(final UnaryOperator<Long> normalizer) {
        Objects.requireNonNull(normalizer, "normalizer");
        return new Codec<Long>() {
            @Override
            public Long normalize(Long value) {
                return normalizer.apply(value);
            }

            @Override
            public String encode(Long value) {
                return value.toString();
            }

            @Override
            public Long decode(String encoded) {
                return Long.valueOf(encoded);
            }
        };
    }

    /** Creates the standard string codec with a caller-supplied normalizer. */
    public static Codec<String> stringCodec(final UnaryOperator<String> normalizer) {
        Objects.requireNonNull(normalizer, "normalizer");
        return new Codec<String>() {
            @Override
            public String normalize(String value) {
                return normalizer.apply(value);
            }

            @Override
            public String encode(String value) {
                return value;
            }

            @Override
            public String decode(String encoded) {
                return encoded;
            }
        };
    }

    /**
     * Checks an untyped integration value, normalizes it, and applies fact validation.
     *
     * @return the normalized typed value
     */
    public T validateRaw(Object rawValue) {
        T normalized = normalizeRaw(rawValue);
        validateNormalized(normalized, encodeNormalized(normalized));
        return normalized;
    }

    private T normalizeRaw(Object rawValue) {
        if (rawValue == null || !valueType.isInstance(rawValue)) {
            throw new IllegalArgumentException("Fact " + key + " requires " + valueType.getName());
        }
        return Objects.requireNonNull(codec.normalize(valueType.cast(rawValue)),
            "Fact normalizer returned null");
    }

    private String encodeNormalized(T normalized) {
        return Objects.requireNonNull(codec.encode(normalized), "Fact codec returned null");
    }

    private void validateNormalized(T normalized, String encoded) {
        if (encoded.length() > maxLength) {
            throw new IllegalArgumentException("Fact " + key + " exceeds maximum length " + maxLength);
        }
        if (!validator.test(normalized)) {
            throw new IllegalArgumentException("Fact " + key + " failed validation");
        }
    }

    /** Validates, normalizes, and encodes a typed value for persistence or event transport. */
    public String encode(T value) {
        T normalized = normalizeRaw(value);
        String encoded = encodeNormalized(normalized);
        validateNormalized(normalized, encoded);
        return encoded;
    }

    /** Validates and encodes a value received through a heterogeneous fact collection. */
    public String encodeRaw(Object value) {
        T normalized = normalizeRaw(value);
        String encoded = encodeNormalized(normalized);
        validateNormalized(normalized, encoded);
        return encoded;
    }

    /** Decodes a persistence value and applies the same normalization and validation path. */
    public T decode(String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        return validateRaw(codec.decode(encoded));
    }

    public Class<? extends FactType<T>> getFactType() {
        return factType;
    }

    public String getKey() {
        return key;
    }

    public Class<T> getValueType() {
        return valueType;
    }

    public Set<FactSource> getAllowedSources() {
        return allowedSources;
    }

    public boolean allows(FactSource source) {
        return allowedSources.contains(Objects.requireNonNull(source, "source"));
    }

    public Sensitivity getSensitivity() {
        return sensitivity;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public Storage getStorage() {
        return storage;
    }

    /** Builder for complete fact persistence and validation metadata. */
    public static final class Builder<T> {
        private final Class<? extends FactType<T>> factType;
        private final String key;
        private final Class<T> valueType;
        private Codec<T> codec;
        private EnumSet<FactSource> allowedSources = EnumSet.noneOf(FactSource.class);
        private Sensitivity sensitivity;
        private int maxLength;
        private Predicate<T> validator = value -> true;
        private Storage storage;

        private Builder(Class<? extends FactType<T>> factType, String key, Class<T> valueType) {
            this.factType = Objects.requireNonNull(factType, "factType");
            this.valueType = Objects.requireNonNull(valueType, "valueType");
            if (key == null || !KEY_PATTERN.matcher(key).matches()) {
                throw new IllegalArgumentException("Fact key must be a stable lowercase key of at most 128 characters");
            }
            this.key = key;
        }

        public Builder<T> codec(Codec<T> codec) {
            this.codec = Objects.requireNonNull(codec, "codec");
            return this;
        }

        public Builder<T> allowedSources(FactSource... sourceValues) {
            if (sourceValues == null || sourceValues.length == 0) {
                throw new IllegalArgumentException("At least one fact source is required");
            }
            EnumSet<FactSource> sources = EnumSet.noneOf(FactSource.class);
            for (FactSource source : sourceValues) {
                sources.add(Objects.requireNonNull(source, "source"));
            }
            this.allowedSources = sources;
            return this;
        }

        public Builder<T> sensitivity(Sensitivity sensitivity) {
            this.sensitivity = Objects.requireNonNull(sensitivity, "sensitivity");
            return this;
        }

        public Builder<T> maxLength(int maxLength) {
            if (maxLength <= 0) {
                throw new IllegalArgumentException("maxLength must be positive");
            }
            this.maxLength = maxLength;
            return this;
        }

        public Builder<T> validator(Predicate<T> validator) {
            this.validator = Objects.requireNonNull(validator, "validator");
            return this;
        }

        public Builder<T> storage(Storage storage) {
            this.storage = Objects.requireNonNull(storage, "storage");
            return this;
        }

        public FactDefinition<T> build() {
            Objects.requireNonNull(codec, "codec");
            if (allowedSources.isEmpty()) {
                throw new IllegalStateException("allowedSources must be configured");
            }
            Objects.requireNonNull(sensitivity, "sensitivity");
            if (maxLength <= 0) {
                throw new IllegalStateException("maxLength must be configured");
            }
            Objects.requireNonNull(storage, "storage");
            return new FactDefinition<T>(this);
        }
    }
}
