package io.github.jasper.monitoring.api.authentication;

/** Transient host-domain input used to derive an internal login subject key. */
public final class LoginSubjectInput {
    private final String loginUser;
    private final String realm;

    public LoginSubjectInput(String loginUser, String realm) {
        this.loginUser = required(loginUser, "loginUser", 256, false);
        this.realm = required(realm, "realm", 128, true);
    }

    public String getLoginUser() {
        return loginUser;
    }

    public String getRealm() {
        return realm;
    }

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
