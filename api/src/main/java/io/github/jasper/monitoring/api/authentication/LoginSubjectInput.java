package io.github.jasper.monitoring.api.authentication;

/**
 * 用于派生内部登录主体密钥的临时宿主输入。
 *
 * <p>{@code loginUser} 仅在当前调用链内参与规范化和 HMAC 派生，不应作为事件、Fact、
 * 告警、控制主体或日志字段持久化；{@code realm} 用于隔离不同认证域中的同名账号。</p>
 */
public final class LoginSubjectInput {
    private final String loginUser;
    private final String realm;

    /**
     * 创建一次认证尝试的主体输入。
     *
     * @param loginUser 用户提交的登录标识，不能为空且最长 256 个字符
     * @param realm 稳定的认证域标识，去除首尾空白后不能为空且最长 128 个字符
     * @throws IllegalArgumentException 任一参数为空、超长或包含控制字符时抛出
     */
    public LoginSubjectInput(String loginUser, String realm) {
        this.loginUser = required(loginUser, "loginUser", 256, false);
        this.realm = required(realm, "realm", 128, true);
    }

    /** @return 用户提交的原始登录标识，仅允许在当前认证调用链内使用 */
    public String getLoginUser() {
        return loginUser;
    }

    /** @return 去除首尾空白后的稳定认证域标识 */
    public String getRealm() {
        return realm;
    }

    /** @return 已隐藏原始登录标识、可安全用于诊断的字符串表示 */
    @Override
    public String toString() {
        return "LoginSubjectInput{loginUser=<redacted>, realm='" + realm + "'}";
    }

    private static String required(String value, String name, int maxLength, boolean trim) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = trim ? value.trim() : value;
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds maximum length " + maxLength);
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (Character.isISOControl(normalized.charAt(i))) {
                throw new IllegalArgumentException(name + " contains control characters");
            }
        }
        return normalized;
    }
}
