package io.github.jasper.monitoring.api.management.query;

import io.github.jasper.monitoring.api.management.ManagementPageRequest;

/** 白名单查询条件。 */
public final class WhitelistQuery extends ManagementQuery {
    /** 白名单列表支持的固定排序字段。 */
    public enum Sort { ID, CREATED_AT }
    private WhitelistQuery(ManagementPageRequest p) {
        super(p);
    }

    /** @return 白名单查询对象 */
    public static WhitelistQuery of(ManagementPageRequest p) {
        return new WhitelistQuery(p);
    }
}
