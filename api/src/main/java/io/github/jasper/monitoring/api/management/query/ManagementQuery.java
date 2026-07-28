package io.github.jasper.monitoring.api.management.query;

import io.github.jasper.monitoring.api.management.ManagementPageRequest;
import java.util.Objects;

/** 管理查询基类，封装统一分页参数。 */
public class ManagementQuery {
    private final ManagementPageRequest page;

    protected ManagementQuery(ManagementPageRequest page) {
        this.page = Objects.requireNonNull(page, "page");
    }

    /** @return 分页请求参数 */
    public ManagementPageRequest getPage() {
        return page;
    }
}
