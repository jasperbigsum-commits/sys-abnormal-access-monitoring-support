package io.github.jasper.monitoring.spring.support;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * 可选地把监测事件的追踪标识同步到宿主日志 MDC。
 *
 * <p>通过反射访问 {@code org.slf4j.MDC}，因此没有日志实现或未使用 MDC 的宿主不会增加运行时依赖。
 * 请求适配器会优先使用传入追踪标识，其次复用已有 MDC 值，最后生成新值；作用域关闭时恢复线程原有值，
 * 防止 Servlet 线程复用时串链。</p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class MdcTraceBridge {
    private final boolean enabled;
    private final String traceIdKey;
    private final MdcAccess access;

    /**
     * 创建可选 MDC 桥接器。
     *
     * @param enabled 是否启用 MDC 同步
     * @param traceIdKey 宿主日志模式中使用的 MDC 键；为空时使用 {@code traceId}
     * @return 可安全在没有 SLF4J MDC 的运行时中使用的桥接器
     */
    public static MdcTraceBridge create(boolean enabled, String traceIdKey) {
        String key = traceIdKey == null || traceIdKey.trim().isEmpty() ? "traceId" : traceIdKey.trim();
        return new MdcTraceBridge(enabled, key, MdcAccess.load());
    }

    /**
     * 读取当前线程中宿主日志已建立的追踪标识。
     *
     * @return 当前 MDC 追踪标识；MDC 不可用或未设置时为 {@code null}
     */
    public String currentTraceId() {
        return enabled && access != null ? access.get(traceIdKey) : null;
    }

    /**
     * 在当前线程绑定追踪标识，并返回用于恢复原 MDC 的作用域。
     *
     * @param traceId 将与监测事件和日志关联的追踪标识
     * @return 必须在请求结束时关闭的作用域；不可用时为无操作作用域
     */
    public Scope bind(String traceId) {
        if (!enabled || access == null || traceId == null || traceId.trim().isEmpty()) {
            return Scope.NOOP;
        }
        String previous = access.get(traceIdKey);
        access.put(traceIdKey, traceId.trim());
        return new RestoreScope(access, traceIdKey, previous);
    }

    /** 请求完成后恢复 MDC 的可关闭作用域。 */
    public interface Scope extends AutoCloseable {
        /** 不修改 MDC 的空作用域。 */
        Scope NOOP = new Scope() {
            @Override
            public void close() {
                // 当前运行时没有可用的 MDC 实现。
            }
        };

        /** 恢复绑定前的 MDC 值。 */
        @Override
        void close();
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class RestoreScope implements Scope {
        private final MdcAccess access;
        private final String key;
        private final String previous;

        @Override
        public void close() {
            if (previous == null) {
                access.remove(key);
            } else {
                access.put(key, previous);
            }
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class MdcAccess {
        private final Method get;
        private final Method put;
        private final Method remove;

        private static MdcAccess load() {
            try {
                Class<?> type = Class.forName("org.slf4j.MDC", false, MdcTraceBridge.class.getClassLoader());
                return new MdcAccess(type.getMethod("get", String.class), type.getMethod("put", String.class, String.class),
                    type.getMethod("remove", String.class));
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
                return null;
            }
        }

        private String get(String key) {
            try {
                return (String) get.invoke(null, key);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                return null;
            }
        }

        private void put(String key, String value) {
            try {
                put.invoke(null, key, value);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // 日志后端故障不能阻断监测或宿主请求。
            }
        }

        private void remove(String key) {
            try {
                remove.invoke(null, key);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // 日志后端故障不能阻断监测或宿主请求。
            }
        }
    }
}
