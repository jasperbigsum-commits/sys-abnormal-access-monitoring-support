package io.github.jasper.monitoring.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Sanitizes values before they become searchable security evidence.
 *
 * <p>This utility removes control characters, bounds field sizes, and rejects attribute keys
 * that suggest credential material. It is intentionally conservative: integrations should pass
 * only identifiers and operational metadata, never request bodies or secrets.</p>
 */
public final class SecurityFieldSanitizer {
    private static final Set<String> FORBIDDEN_KEYWORDS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
        "password", "passwd", "token", "cookie", "secret", "credential", "authorization", "private_key", "apikey", "api_key", "sms_code")));

    private SecurityFieldSanitizer() {
    }

    /**
     * Normalizes whitespace and control characters, then truncates a value.
     *
     * @param value source text, possibly {@code null}
     * @param maximumLength maximum number of characters retained
     * @return sanitized text, or {@code null} when {@code value} is {@code null}
     */
    public static String text(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        StringBuilder sanitized = new StringBuilder(value.length());
        boolean previousWhitespace = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)) {
                character = ' ';
            }
            if (Character.isWhitespace(character)) {
                if (!previousWhitespace) {
                    sanitized.append(' ');
                    previousWhitespace = true;
                }
            } else {
                sanitized.append(character);
                previousWhitespace = false;
            }
            if (sanitized.length() >= maximumLength) {
                break;
            }
        }
        return sanitized.toString().trim();
    }

    /**
     * Verifies that an attribute key is non-empty and does not identify credential material.
     *
     * @param key attribute key to validate
     * @throws IllegalArgumentException if the key is blank or contains a forbidden keyword
     */
    public static void requireSafeAttributeKey(String key) {
        String normalized = text(key, 128);
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException("Event attribute key is required");
        }
        String lowerCase = normalized.toLowerCase(Locale.ROOT);
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (lowerCase.contains(keyword)) {
                throw new IllegalArgumentException("Event attributes must not contain credential material: " + normalized);
            }
        }
    }
}
