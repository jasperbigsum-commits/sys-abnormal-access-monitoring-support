package io.github.jasper.monitoring.api.code;

/** Stable code contract shared by persisted and transient monitoring values. */
public interface GovernedCode {
    String getCode();
    CodeFamily getFamily();
}
