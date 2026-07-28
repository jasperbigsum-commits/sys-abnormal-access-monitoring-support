package io.github.jasper.monitoring.api.management;

import io.github.jasper.monitoring.api.management.model.SecurityEventView;
import io.github.jasper.monitoring.api.management.query.SecurityEventQuery;

/**
 * 安全事件的只读管理查询边界。
 *
 * <p>实现需在持久化访问前完成授权校验，并且仅返回脱敏后的字段。</p>
 */
public interface SecurityEventQueryService {
    /** @return 当前操作者可见范围内的事件分页结果 */
    ManagementPage<SecurityEventView> search(ManagementActor actor, SecurityEventQuery query);
    /** @return 指定事件标识对应的事件视图 */
    SecurityEventView get(ManagementActor actor, String eventId);
}
