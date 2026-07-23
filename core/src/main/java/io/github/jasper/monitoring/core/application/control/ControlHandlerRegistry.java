package io.github.jasper.monitoring.core.application.control;

import io.github.jasper.monitoring.core.port.ControlHandler;


import io.github.jasper.monitoring.api.ControlActionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 按控制动作类型解析宿主控制处理器。
 *
 * <p>处理器列表顺序即优先级；当多个处理器支持同一动作时，仅使用第一个。</p>
 */
public final class ControlHandlerRegistry {
    private final List<ControlHandler> handlers;
    /**
     * @param handlers 已排序的宿主控制处理器；构造时会防御性复制列表
     */
    public ControlHandlerRegistry(List<ControlHandler> handlers) {
        this.handlers = Collections.unmodifiableList(new ArrayList<ControlHandler>(handlers));
    }
    /** @return 不含处理器的注册表，仅适用于观察模式 */
    public static ControlHandlerRegistry empty() { return new ControlHandlerRegistry(Collections.<ControlHandler>emptyList()); }
    /**
     * @param action 待解析的控制动作
     * @return 支持该动作的第一个已配置处理器；不存在时为空
     */
    public Optional<ControlHandler> find(ControlActionType action) {
        for (ControlHandler handler : handlers) {
            if (handler.supports(action)) { return Optional.of(handler); }
        }
        return Optional.empty();
    }
    /**
     * @param action 待判断的控制动作
     * @return 是否有任一已配置处理器支持该动作
     */
    public boolean supports(ControlActionType action) { return find(action).isPresent(); }
    /** @return 是否没有任何已配置处理器支持可执行的控制动作 */
    public boolean isEmpty() {
        for (ControlHandler handler : handlers) {
            for (ControlActionType action : ControlActionType.values()) {
                if (action != ControlActionType.RECORD && handler.supports(action)) {
                    return false;
                }
            }
        }
        return true;
    }
}
