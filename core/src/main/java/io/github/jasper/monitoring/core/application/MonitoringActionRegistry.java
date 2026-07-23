package io.github.jasper.monitoring.core.application;


import io.github.jasper.monitoring.api.MonitorActionDefinition;
import io.github.jasper.monitoring.api.SecurityFieldSanitizer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 手工埋点动作的启动期注册表。
 *
 * <p>Controller 注解和服务、消息、任务中的方法调用都使用 {@link MonitorActionDefinition} 表达静态元数据。
 * 注册表按稳定动作编码去重，拒绝同一编码注册不同定义，防止同一审计维度在不同调用点出现不同事件类别。
 * 注册表不保存动态业务事实；资源 ID、数据量和原因码由每次方法调用的草稿补充。</p>
 */
public final class MonitoringActionRegistry {
    private final Map<String, MonitorActionDefinition> actions = new LinkedHashMap<String, MonitorActionDefinition>();

    /**
     * 注册一个稳定的动作定义。
     *
     * @param definition 要在当前宿主进程中复用的静态动作定义
     * @return 当前注册表，便于在配置类中链式声明
     * @throws IllegalStateException 当同一动作编码被注册为不一致定义时抛出
     */
    public synchronized MonitoringActionRegistry register(MonitorActionDefinition definition) {
        MonitorActionDefinition value = Objects.requireNonNull(definition, "definition");
        MonitorActionDefinition previous = actions.get(value.getAction());
        if (previous != null && !previous.equals(value)) {
            throw new IllegalStateException("Conflicting monitoring action definition: " + value.getAction());
        }
        actions.put(value.getAction(), value);
        return this;
    }

    /**
     * 按动作编码查找定义。
     *
     * @param action 宿主的稳定动作编码
     * @return 已注册定义；不存在时为空
     */
    public synchronized Optional<MonitorActionDefinition> find(String action) {
        return Optional.ofNullable(actions.get(normalizeAction(action)));
    }

    /**
     * 获取一个已注册动作定义。
     *
     * @param action 宿主的稳定动作编码
     * @return 已注册定义
     * @throws IllegalArgumentException 当动作编码为空或尚未注册时抛出
     */
    public synchronized MonitorActionDefinition require(String action) {
        String key = normalizeAction(action);
        MonitorActionDefinition definition = actions.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Monitoring action is not registered: " + key);
        }
        return definition;
    }

    /**
     * 返回当前注册内容的不可变快照，主要用于启动诊断和接入验收。
     *
     * @return 按注册顺序排列的动作编码到定义的映射
     */
    public synchronized Map<String, MonitorActionDefinition> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, MonitorActionDefinition>(actions));
    }

    private static String normalizeAction(String action) {
        String value = SecurityFieldSanitizer.text(action, 128);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Monitoring action is required");
        }
        return value;
    }
}
