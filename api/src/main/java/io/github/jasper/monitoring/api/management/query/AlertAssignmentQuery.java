package io.github.jasper.monitoring.api.management.query;

import io.github.jasper.monitoring.api.management.ManagementPageRequest;
import java.util.Objects;

/** Bounded query for one alert's assignment history. */
public final class AlertAssignmentQuery {
    public enum Sort { CREATED_AT, ID }
    private final ManagementPageRequest page;

    private AlertAssignmentQuery(ManagementPageRequest page) {
        this.page = Objects.requireNonNull(page, "page");
        if (!(page.getSort() instanceof Sort)) throw new IllegalArgumentException("invalid assignment sort");
    }

    public static AlertAssignmentQuery of(ManagementPageRequest page) { return new AlertAssignmentQuery(page); }
    public ManagementPageRequest getPage() { return page; }
}
