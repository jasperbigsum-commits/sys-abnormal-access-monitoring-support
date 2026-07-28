package io.github.jasper.monitoring.api.management;

import io.github.jasper.monitoring.api.management.command.AlertAcknowledgeCommand;
import io.github.jasper.monitoring.api.management.command.AlertAssignmentCommand;
import io.github.jasper.monitoring.api.management.command.AlertCloseCommand;
import io.github.jasper.monitoring.api.management.command.AlertFalsePositiveCommand;
import io.github.jasper.monitoring.api.management.command.AlertStartInvestigationCommand;
import io.github.jasper.monitoring.api.management.model.AlertAssignmentView;
import io.github.jasper.monitoring.api.management.model.AlertView;
import io.github.jasper.monitoring.api.management.query.AlertAssignmentQuery;
import io.github.jasper.monitoring.api.management.query.AlertQuery;

/**
 * 告警查询与版本化处置命令的管理边界，供宿主 Controller 适配器直接使用。
 *
 * <p>每个方法都应在访问持久化前校验操作者作用域。写操作采用乐观锁并追加处置历史，
 * 同时在同一事务内提交成功审计记录。</p>
 */
public interface AlertManagementService {
    /** @return 当前操作者系统作用域内可见的告警分页结果 */
    ManagementPage<AlertView> search(ManagementActor actor, AlertQuery query);
    /** @return 授权通过后读取到的单条告警视图 */
    AlertView get(ManagementActor actor, String alertId);
    /** @return 告警的仅追加分配历史分页结果 */
    ManagementPage<AlertAssignmentView> assignmentHistory(ManagementActor actor, String alertId, AlertAssignmentQuery query);
    /** @return 分配并进入排查后的告警视图 */
    AlertView assign(ManagementActor actor, AlertAssignmentCommand command);
    /** @return 记录确认后的告警视图（不覆盖历史处置） */
    AlertView acknowledge(ManagementActor actor, AlertAcknowledgeCommand command);
    /** @return 开始排查后的告警视图（可不指定处理人） */
    AlertView startInvestigation(ManagementActor actor, AlertStartInvestigationCommand command);
    /** @return 按期望版本关闭后的告警视图 */
    AlertView close(ManagementActor actor, AlertCloseCommand command);
    /** @return 标记误报后的告警视图（保留证据与处置历史） */
    AlertView markFalsePositive(ManagementActor actor, AlertFalsePositiveCommand command);
}
