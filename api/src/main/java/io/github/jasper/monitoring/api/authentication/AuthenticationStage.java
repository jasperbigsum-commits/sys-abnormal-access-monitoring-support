package io.github.jasper.monitoring.api.authentication;

/** Authentication checkpoint that produced the final outcome. */
public enum AuthenticationStage {
    CREDENTIAL,
    CAPTCHA,
    MFA
}
