package io.github.jasper.monitoring.core.application.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.jasper.monitoring.api.authentication.LoginSubjectCanonicalizer;
import io.github.jasper.monitoring.api.authentication.LoginSubjectInput;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LoginSubjectKeyFactoryTest {
    private static final byte[] SECRET = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);

    @Test
    void generatesDeterministicOpaqueKeyWithoutRawLogin() {
        LoginSubjectKeyFactory factory = new LoginSubjectKeyFactory(SECRET, canonicalizer());

        String key = factory.generate(new LoginSubjectInput(" Alice ", "primary"));

        assertTrue(key.startsWith("v1:"));
        assertTrue(!key.contains("Alice") && !key.contains("alice"));
        assertEquals(key, factory.generate(new LoginSubjectInput("Alice", "primary")));
    }

    @Test
    void isolatesRealmAndSecret() {
        LoginSubjectInput input = new LoginSubjectInput("Alice", "primary");

        String primary = new LoginSubjectKeyFactory(SECRET, canonicalizer()).generate(input);
        String otherRealm = new LoginSubjectKeyFactory(SECRET, canonicalizer())
                .generate(new LoginSubjectInput("Alice", "secondary"));
        String otherSecret = new LoginSubjectKeyFactory(
                "abcdefghijklmnopqrstuvwxyz123456".getBytes(StandardCharsets.UTF_8), canonicalizer())
                .generate(input);

        assertNotEquals(primary, otherRealm);
        assertNotEquals(primary, otherSecret);
    }

    @Test
    void requiresStrongSecretAndCopiesIt() {
        byte[] secret = Arrays.copyOf(SECRET, SECRET.length);
        LoginSubjectKeyFactory factory = new LoginSubjectKeyFactory(secret, canonicalizer());
        String before = factory.generate(new LoginSubjectInput("Alice", "primary"));
        secret[0] = 'x';

        assertEquals(before, factory.generate(new LoginSubjectInput("Alice", "primary")));
        assertThrows(IllegalArgumentException.class,
                () -> new LoginSubjectKeyFactory(new byte[31], canonicalizer()));
    }

    private static LoginSubjectCanonicalizer canonicalizer() {
        return subject -> subject.getLoginUser().trim().toLowerCase(java.util.Locale.ROOT);
    }
}
