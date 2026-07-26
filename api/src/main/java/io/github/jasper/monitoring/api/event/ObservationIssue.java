package io.github.jasper.monitoring.api.event;

import io.github.jasper.monitoring.api.SecurityFieldSanitizer;
import java.util.Objects;

/** Stable, non-sensitive diagnostic explaining why an observation is incomplete. */
public final class ObservationIssue {
    private final String code;
    private final String detail;
    public ObservationIssue(String code, String detail) {
        this.code = Objects.requireNonNull(SecurityFieldSanitizer.text(code, 128), "code");
        this.detail = SecurityFieldSanitizer.text(detail, 256);
    }
    public String getCode() { return code; }
    public String getDetail() { return detail; }
}
