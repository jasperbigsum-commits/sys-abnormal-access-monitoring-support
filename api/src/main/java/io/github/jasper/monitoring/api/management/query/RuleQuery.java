package io.github.jasper.monitoring.api.management.query;

import io.github.jasper.monitoring.api.management.ManagementPageRequest;

/** 规则目录查询条件。 */
public final class RuleQuery extends ManagementQuery {
    private RuleQuery(ManagementPageRequest p) {
        super(p);
    }

    /** @return 规则查询对象 */
    public static RuleQuery of(ManagementPageRequest p) {
        return new RuleQuery(p);
    }
}
