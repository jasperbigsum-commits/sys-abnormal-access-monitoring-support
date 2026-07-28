package io.github.jasper.monitoring.api.management.query;

import io.github.jasper.monitoring.api.management.ManagementPageRequest;
import java.util.Objects;

/** 告警列表查询条件。 */
public final class AlertQuery {
    /** 告警列表支持的排序字段。 */
    public enum Sort { CREATED_AT, SEVERITY, STATUS, ID }

    private final ManagementPageRequest page;

    private AlertQuery(ManagementPageRequest page) {
        this.page = Objects.requireNonNull(page, "page");
    }

    /** @return 告警查询对象 */
    public static AlertQuery of(ManagementPageRequest page) {
        return new AlertQuery(page);
    }

    /** @return 分页参数 */
    public ManagementPageRequest getPage() {
        return page;
    }
}
