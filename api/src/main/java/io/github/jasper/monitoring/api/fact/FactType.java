package io.github.jasper.monitoring.api.fact;

/** Identifies a typed fact available to monitoring rules. */
public interface FactType<T> {

    /** @return the runtime class accepted for this fact's values */
    Class<T> valueType();
}
