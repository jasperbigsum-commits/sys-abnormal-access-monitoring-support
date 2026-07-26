package io.github.jasper.monitoring.core.domain.event;

import io.github.jasper.monitoring.api.SecurityFieldSanitizer;

/** Internal event-boundary normalization; host code must use typed contracts instead. */
final class SecurityValueNormalizer {
    private SecurityValueNormalizer() {
    }

    static String text(String value, int maximumLength) {
        return SecurityFieldSanitizer.text(value, maximumLength);
    }
}
