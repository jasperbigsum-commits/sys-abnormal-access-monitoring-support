package io.github.jasper.monitoring.api.action;

/** Synchronous decision for whether the current action attempt may continue. */
public enum ActionDisposition {
    ALLOW,
    BLOCK
}
