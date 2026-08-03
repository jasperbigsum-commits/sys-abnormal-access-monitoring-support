package io.github.jasper.monitoring.api.authentication;

/** 产生最终认证结果的校验阶段。 */
public enum AuthenticationStage {
    /** 账号、密码或其他主凭据校验阶段。 */
    CREDENTIAL,
    /** 图形、短信或其他验证码校验阶段。 */
    CAPTCHA,
    /** 多因素认证校验阶段。 */
    MFA
}
