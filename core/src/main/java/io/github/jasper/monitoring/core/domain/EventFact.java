package io.github.jasper.monitoring.core.domain;

import io.github.jasper.monitoring.api.fact.FactSource;
import java.util.Objects;

/** Immutable, codec-produced fact snapshot persisted as part of a security event. */
public final class EventFact {
    private final String key;
    private final String valueType;
    private final String valueText;
    private final FactSource source;

    public EventFact(String key, String valueType, String valueText, FactSource source) {
        this.key = bounded(key, "key", 128);
        this.valueType = bounded(valueType, "valueType", 256);
        this.valueText = bounded(valueText, "valueText", 2048);
        this.source = Objects.requireNonNull(source, "source");
    }

    public String getKey() { return key; }
    public String getValueType() { return valueType; }
    public String getValueText() { return valueText; }
    public FactSource getSource() { return source; }

    private static String bounded(String value, String name, int maximum) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty() || value.length() > maximum) {
            throw new IllegalArgumentException(name + " is blank or too long");
        }
        return value;
    }
}
