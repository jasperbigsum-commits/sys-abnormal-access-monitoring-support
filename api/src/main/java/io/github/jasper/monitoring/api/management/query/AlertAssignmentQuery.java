package io.github.jasper.monitoring.api.management.query;

import io.github.jasper.monitoring.api.management.ManagementPageRequest;
import java.util.Objects;

/** 单条告警分配历史的分页查询条件。 */
public final class AlertAssignmentQuery {
    /** 分配历史支持的排序字段。 */
    public enum Sort { CREATED_AT, ID }
    private final ManagementPageRequest page;

    private AlertAssignmentQuery(ManagementPageRequest page) {
        this.page = Objects.requireNonNull(page, "page");
        if (!(page.getSort() instanceof Sort)) throw new IllegalArgumentException("invalid assignment sort");
    }

    /** @return 分配历史查询对象 */
    public static AlertAssignmentQuery of(ManagementPageRequest page) { return new AlertAssignmentQuery(page); }
    /** @return 分页参数 */
    public ManagementPageRequest getPage() { return page; }
}
