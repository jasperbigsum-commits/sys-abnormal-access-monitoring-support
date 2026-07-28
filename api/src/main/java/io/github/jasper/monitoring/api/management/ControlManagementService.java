package io.github.jasper.monitoring.api.management;

import io.github.jasper.monitoring.api.management.command.ControlApprovalCommand;
import io.github.jasper.monitoring.api.management.command.ControlExecutionCommand;
import io.github.jasper.monitoring.api.management.command.ControlRejectionCommand;
import io.github.jasper.monitoring.api.management.command.ControlRetryCommand;
import io.github.jasper.monitoring.api.management.model.ControlExecutionView;
import io.github.jasper.monitoring.api.management.model.ControlView;
import io.github.jasper.monitoring.api.management.query.ControlQuery;

/**
 * 控制动作生命周期的授权管理边界，供宿主 Controller 适配器直接调用。
 *
 * <p>任何状态访问或宿主侧副作用执行前，都必须先完成授权。</p>
 */
public interface ControlManagementService {
    /** @return 当前操作者可见范围内的控制记录分页结果 */
    ManagementPage<ControlView> search(ManagementActor actor, ControlQuery query);

    /** @return 指定控制记录的当前视图 */
    ControlView get(ManagementActor actor, String id);

    /** @return 审批后的控制记录视图 */
    ControlView approve(ManagementActor actor, ControlApprovalCommand command);

    /** @return 驳回后的控制记录视图 */
    ControlView reject(ManagementActor actor, ControlRejectionCommand command);

    /** @return 触发失败重试后的控制记录视图 */
    ControlView retryFailed(ManagementActor actor, ControlRetryCommand command);

    /** @return 新建并执行手工控制（如会话吊销）后的执行结果 */
    ControlExecutionView execute(ManagementActor actor, ControlExecutionCommand command);
}
