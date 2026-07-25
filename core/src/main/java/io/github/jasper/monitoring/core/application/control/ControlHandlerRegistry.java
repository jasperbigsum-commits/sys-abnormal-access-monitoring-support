package io.github.jasper.monitoring.core.application.control;

import io.github.jasper.monitoring.core.port.ControlHandler;


import io.github.jasper.monitoring.api.ControlActionType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 按控制动作类型解析宿主控制处理器。
 *
 * <p>宿主处理器始终优先于默认回退处理器；同一层内按列表顺序选择第一个支持动作的处理器。</p>
 */
public final class ControlHandlerRegistry {
    private final List<ControlHandler> hostHandlers;
    private final List<ControlHandler> genericHandlers;
    private final List<ControlHandler> defaultHandlers;

    /**
     * @param handlers 已排序的宿主控制处理器；构造时会防御性复制列表
     */
    public ControlHandlerRegistry(List<ControlHandler> handlers) {
        this(handlers, Collections.<ControlHandler>emptyList());
    }

    /**
     * 创建具有宿主优先级和默认回退层的注册表。
     *
     * <p>同一动作先从 {@code hostHandlers} 解析；仅当宿主未声明该动作时，才会使用
     * {@code defaultHandlers}。默认处理器不会使 {@link #isEmpty()} 返回 {@code false}。</p>
     *
     * @param hostHandlers 已排序的宿主控制处理器
     * @param defaultHandlers 已排序的框架默认回退处理器
     */
    public ControlHandlerRegistry(List<ControlHandler> hostHandlers, List<ControlHandler> defaultHandlers) {
        this(hostHandlers, Collections.<ControlHandler>emptyList(), defaultHandlers);
    }

    /**
     * 创建具有宿主、显式通用控制和默认回退三层优先级的注册表。
     *
     * @param hostHandlers 已排序的宿主控制处理器
     * @param genericHandlers 已排序且经过配置校验的通用控制处理器
     * @param defaultHandlers 已排序的框架默认回退处理器
     */
    public ControlHandlerRegistry(List<ControlHandler> hostHandlers, List<ControlHandler> genericHandlers,
                                  List<ControlHandler> defaultHandlers) {
        this.hostHandlers = immutableCopy(hostHandlers, "hostHandlers");
        this.genericHandlers = immutableCopy(genericHandlers, "genericHandlers");
        this.defaultHandlers = immutableCopy(defaultHandlers, "defaultHandlers");
    }
    /** @return 不含处理器的注册表，仅适用于观察模式 */
    public static ControlHandlerRegistry empty() { return new ControlHandlerRegistry(Collections.<ControlHandler>emptyList()); }
    /**
     * @param action 待解析的控制动作
     * @return 支持该动作的第一个已配置处理器；不存在时为空
     */
    public Optional<ControlHandler> find(ControlActionType action) {
        Optional<ControlHandler> host = find(hostHandlers, action);
        if (host.isPresent()) {
            return host;
        }
        Optional<ControlHandler> generic = find(genericHandlers, action);
        return generic.isPresent() ? generic : find(defaultHandlers, action);
    }
    /**
     * @param action 待判断的控制动作
     * @return 是否有任一已配置处理器支持该动作
     */
    public boolean supports(ControlActionType action) { return find(action).isPresent(); }
    /** @return 是否没有任何已配置处理器支持可执行的控制动作 */
    public boolean isEmpty() {
        return !hasExecutableHandler(hostHandlers) && !hasExecutableHandler(genericHandlers);
    }

    private static boolean hasExecutableHandler(List<ControlHandler> handlers) {
        for (ControlHandler handler : handlers) {
            if (handler.isFallback()) {
                continue;
            }
            for (ControlActionType action : ControlActionType.values()) {
                if (action.requiresHostHandler() && handler.supports(action)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Optional<ControlHandler> find(List<ControlHandler> handlers, ControlActionType action) {
        for (ControlHandler handler : handlers) {
            if (handler.supports(action)) {
                return Optional.of(handler);
            }
        }
        return Optional.empty();
    }

    private static List<ControlHandler> immutableCopy(List<ControlHandler> handlers, String name) {
        Objects.requireNonNull(handlers, name);
        return Collections.unmodifiableList(new ArrayList<ControlHandler>(handlers));
    }
}
