package io.github.jasper.monitoring.core.port;

import io.github.jasper.monitoring.core.domain.ControlCommand;


import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.api.ControlActionType;

/**
 * 宿主系统执行限流、会话失效、锁定、MFA 或拒绝等具体控制动作的接入点。
 *
 * <p>实现必须仅在 {@link ControlCommand} 指定的作用域内生效，并返回非空结果。宿主仍然负责
 * 最终的认证、授权和事务边界，监测组件不会替代业务安全决策。</p>
 */
public interface ControlHandler {
    /**
     * 判断当前处理器是否只是框架提供的安全回退。
     *
     * <p>回退处理器仅用于为未接入的动作留下可审计结果，不代表宿主具备实际控制能力，
     * 因此不会使 {@code ENFORCE} 模式通过启动校验。</p>
     *
     * @return 当前处理器仅为回退时为 {@code true}
     */
    default boolean isFallback() {
        return false;
    }

    /**
     * 判断当前处理器是否能执行指定动作。
     *
     * @param action 控制动作类型
     * @return 能执行该动作时为 {@code true}
     */
    boolean supports(ControlActionType action);

    /**
     * 在宿主系统内执行一条控制指令。
     *
     * @param command 不可变且带幂等键的控制指令
     * @return 非空执行结果；可预期失败应使用 {@link ControlExecution#failed(String, String)} 返回
     */
    ControlExecution execute(ControlCommand command);
}
