package io.github.jasper.monitoring.audit.spring2.management;

import io.github.jasper.monitoring.api.management.ManagementPageRequest;
import java.time.Instant;

/** Parses the zero-based management query parameters used by admin-web. */
final class ManagementHttpParameters {
    private ManagementHttpParameters() { }

    static ManagementPageRequest page(Integer page, Integer size, Enum<?> sort) {
        return ManagementPageRequest.of(page == null ? 0 : page.intValue(), size == null ? 20 : size.intValue(), sort, true);
    }

    static Instant from(String from) {
        return from == null || from.trim().isEmpty() ? Instant.now().minusSeconds(24L * 60L * 60L) : Instant.parse(from);
    }

    static Instant to(String to) {
        return to == null || to.trim().isEmpty() ? Instant.now() : Instant.parse(to);
    }
}
