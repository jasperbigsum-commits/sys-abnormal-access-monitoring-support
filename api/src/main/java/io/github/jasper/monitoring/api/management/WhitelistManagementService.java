package io.github.jasper.monitoring.api.management;

import io.github.jasper.monitoring.api.management.command.WhitelistGrantCommand;
import io.github.jasper.monitoring.api.management.command.WhitelistRevokeCommand;
import io.github.jasper.monitoring.api.management.model.WhitelistView;
import io.github.jasper.monitoring.api.management.query.WhitelistQuery;

/**
 * 白名单条目的版本化生命周期管理边界。
 *
 * <p>授权放行仅启用宿主预置条目，不从客户端请求创建规则、主体或 TTL 数据。</p>
 */
public interface WhitelistManagementService {
    /** @return 当前操作者可见范围内的白名单分页结果 */
    ManagementPage<WhitelistView> search(ManagementActor actor, WhitelistQuery query);
    /** @return 指定白名单条目的当前视图 */
    WhitelistView get(ManagementActor actor, String id);
    /** @return 授权放行后的白名单条目视图 */
    WhitelistView grant(ManagementActor actor, WhitelistGrantCommand command);
    /** @return 撤销后的白名单条目视图 */
    WhitelistView revoke(ManagementActor actor, WhitelistRevokeCommand command);
}
