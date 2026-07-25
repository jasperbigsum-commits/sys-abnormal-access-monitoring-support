package io.github.jasper.monitoring.core.application.control;

import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import io.github.jasper.monitoring.core.domain.ControlCommand;
import io.github.jasper.monitoring.core.domain.ControlExecution;
import io.github.jasper.monitoring.core.port.ControlHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 由 {@link ControlActionType} 驱动的安全默认触发器。
 *
 * <p>每个非 {@link ControlActionType#RECORD} 动作都有一个对应实例。它不会尝试猜测宿主的
 * 限流、会话、授权或审批语义，而是返回可审计的 {@code SKIPPED} 结果。注册表始终优先解析
 * 宿主处理器，因此宿主 {@link ControlHandler} 或 {@code @ControlTrigger} 绑定可直接覆盖同一动作。
 * 回退触发器本身不满足 {@code ENFORCE} 的宿主控制能力要求。</p>
 */
public final class DefaultControlActionTrigger implements ControlHandler {
    private static final List<ControlHandler> DEFAULTS = createDefaults();

    private final ControlActionType action;

    private DefaultControlActionTrigger(ControlActionType action) {
        this.action = Objects.requireNonNull(action, "action");
        if (!action.requiresHostHandler()) {
            throw new MonitoringValidationException(MonitoringErrorCode.INVALID_CONTROL_TRIGGER,
                "RECORD does not have a default control trigger");
        }
    }

    /**
     * 返回指定动作的默认回退触发器。
     *
     * @param action 非 {@link ControlActionType#RECORD} 的控制动作
     * @return 仅记录跳过结果的默认触发器
     */
    public static DefaultControlActionTrigger forAction(ControlActionType action) {
        return new DefaultControlActionTrigger(action);
    }

    /**
     * 返回全部非 {@code RECORD} 动作的默认回退触发器。
     *
     * @return 不可变的默认触发器列表
     */
    public static List<ControlHandler> defaults() {
        return DEFAULTS;
    }

    @Override
    public boolean isFallback() {
        return true;
    }

    @Override
    public boolean supports(ControlActionType candidate) {
        return action == candidate;
    }

    @Override
    public ControlExecution execute(ControlCommand command) {
        Objects.requireNonNull(command, "command");
        if (!supports(command.getAction())) {
            return ControlExecution.failed(command.getIdempotencyKey(),
                "DEFAULT_TRIGGER_UNSUPPORTED_ACTION:" + command.getAction());
        }
        return ControlExecution.fallbackSkipped(command.getIdempotencyKey(), action);
    }

    private static List<ControlHandler> createDefaults() {
        List<ControlHandler> values = new ArrayList<ControlHandler>();
        for (ControlActionType action : ControlActionType.values()) {
            if (action.requiresHostHandler()) {
                values.add(new DefaultControlActionTrigger(action));
            }
        }
        return Collections.unmodifiableList(values);
    }
}
