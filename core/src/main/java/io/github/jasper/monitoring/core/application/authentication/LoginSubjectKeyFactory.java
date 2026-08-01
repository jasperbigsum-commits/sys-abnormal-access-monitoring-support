package io.github.jasper.monitoring.core.application.authentication;

import io.github.jasper.monitoring.api.authentication.LoginSubjectCanonicalizer;
import io.github.jasper.monitoring.api.authentication.LoginSubjectInput;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Derives a stable, opaque subject key for authentication controls and facts. */
public final class LoginSubjectKeyFactory {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] secret;
    private final LoginSubjectCanonicalizer canonicalizer;

    public LoginSubjectKeyFactory(byte[] secret, LoginSubjectCanonicalizer canonicalizer) {
        Objects.requireNonNull(secret, "secret");
        if (secret.length < 32) {
            throw new IllegalArgumentException("authentication subject key secret must contain at least 32 bytes");
        }
        this.secret = secret.clone();
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    public String generate(LoginSubjectInput input) {
        Objects.requireNonNull(input, "input");
        String canonical = Objects.requireNonNull(canonicalizer.canonicalize(input), "canonical subject");
        if (canonical.trim().isEmpty() || canonical.length() > 256) {
            throw new IllegalArgumentException("canonical subject is blank or too long");
        }
        for (int i = 0; i < canonical.length(); i++) {
            if (Character.isISOControl(canonical.charAt(i))) {
                throw new IllegalArgumentException("canonical subject contains control characters");
            }
        }
        String payload = input.getRealm() + "\u0000" + canonical;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return "v1:" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC subject key generation unavailable", ex);
        }
    }
}
