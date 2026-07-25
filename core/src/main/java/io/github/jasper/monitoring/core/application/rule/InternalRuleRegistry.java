package io.github.jasper.monitoring.core.application.rule;




import io.github.jasper.monitoring.core.application.DefaultSecurityMonitor;
import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import io.github.jasper.monitoring.api.SecurityFieldSanitizer;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringStateException;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 保存内部代码规则的启动期可写、运行期不可变快照。
 *
 * <p>注册器以规则 ID 去重，在 {@link #freeze()} 后拒绝任何新增或替换操作。{@link DefaultSecurityMonitor}
 * 使用其快照构造后，规则列表不会因管理查询、数据库配置或请求线程而变化。持久化规则的版本、启停和审批
 * 是独立管理模型，不能绕过此不变性。</p>
 */
public final class InternalRuleRegistry implements InternalRuleRegistrar {
    private final Map<String, DetectionRule> rules = new LinkedHashMap<String, DetectionRule>();
    private boolean frozen;

    /** 创建空的内部规则注册器。 */
    public InternalRuleRegistry() {
    }

    /**
     * 创建并注册一组内部规则。
     *
     * @param initialRules 启动期默认或宿主代码规则
     */
    public InternalRuleRegistry(Collection<? extends DetectionRule> initialRules) {
        if (initialRules != null) {
            for (DetectionRule rule : initialRules) {
                register(rule);
            }
        }
    }

    /**
     * 注册内部规则；同一规则 ID 不允许重复。
     *
     * @param rule 确定性、无副作用的规则
     * @throws IllegalStateException 当注册器已冻结或规则 ID 重复时抛出
     */
    @Override
    public synchronized void register(DetectionRule rule) {
        if (frozen) {
            throw new MonitoringStateException(MonitoringErrorCode.RULE_REGISTRY_FROZEN,
                "Internal rule registry is frozen");
        }
        DetectionRule value = Objects.requireNonNull(rule, "rule");
        String ruleId = normalizeRuleId(value.getRuleId());
        if (rules.containsKey(ruleId)) {
            throw new MonitoringConfigurationException(MonitoringErrorCode.DUPLICATE_INTERNAL_RULE_ID,
                "Duplicate internal rule id");
        }
        rules.put(ruleId, value);
    }

    /**
     * 冻结注册器并返回自身。
     *
     * <p>该操作可重复调用。冻结后仍可读取 {@link #rules()}，但任何 {@link #register(DetectionRule)} 调用
     * 都会失败。</p>
     *
     * @return 已冻结的注册器
     */
    public synchronized InternalRuleRegistry freeze() {
        frozen = true;
        return this;
    }

    /**
     * 返回按注册顺序排列的不可变规则快照。
     *
     * @return 当前内部规则快照
     */
    public synchronized List<DetectionRule> rules() {
        return Collections.unmodifiableList(new ArrayList<DetectionRule>(rules.values()));
    }

    /**
     * 返回供管理端展示的内部规则不可变条目。
     *
     * @return 按注册顺序排列，来源固定为 {@code INTERNAL}、可变性固定为 {@code false} 的条目
     */
    public synchronized List<InternalRuleEntry> entries() {
        List<InternalRuleEntry> values = new ArrayList<InternalRuleEntry>();
        for (String ruleId : rules.keySet()) {
            values.add(new InternalRuleEntry(ruleId));
        }
        return Collections.unmodifiableList(values);
    }

    /**
     * 判断注册器是否已冻结。
     *
     * @return 冻结后为 {@code true}
     */
    public synchronized boolean isFrozen() {
        return frozen;
    }

    private static String normalizeRuleId(String ruleId) {
        String value = SecurityFieldSanitizer.text(ruleId, 128);
        if (value == null || value.isEmpty()) {
            throw new MonitoringValidationException(MonitoringErrorCode.REQUIRED_FIELD_MISSING,
                "DetectionRule ruleId is required");
        }
        return value;
    }
}
