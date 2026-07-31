package io.github.jasper.monitoring.api.action;

/** A condition that must be satisfied before a new action attempt may proceed. */
public enum ActionRequirement {
    APPROVAL,
    MFA,
    CAPTCHA
}
