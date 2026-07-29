package io.github.jasper.monitoring.api.fact;

import io.github.jasper.monitoring.api.action.ActionContract;
import io.github.jasper.monitoring.api.action.ActionType;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Fact 绑定定义：显式声明“谁提供、给谁用、产出哪些 Fact、按什么来源记账”。
 * <p>
 * 真实场景：
 * - 把某 Provider 绑定到 ReportExport，仅对导出动作计算 baselineRatio；<br>
 * - 把某 Provider 绑定到 ExportContract，所有导出类动作共享 dataCount 采集规则。
 */
public final class FactBinding {
    private final Class<? extends ActionType> actionType;
    private final Class<? extends ActionContract> contractType;
    private final ActionFactProvider provider;
    private final FactSource source;
    private final Set<Class<? extends FactType<?>>> declaredFacts;

    private FactBinding(Class<? extends ActionType> actionType,
            Class<? extends ActionContract> contractType, FactSource source, ActionFactProvider provider,
            Class<? extends FactType<?>>[] declaredFacts) {
        this.actionType = actionType;
        this.contractType = contractType;
        this.provider = Objects.requireNonNull(provider, "provider");
        this.source = Objects.requireNonNull(source, "source");
        if (declaredFacts == null || declaredFacts.length == 0) {
            throw new IllegalArgumentException("At least one declared fact is required");
        }
        LinkedHashSet<Class<? extends FactType<?>>> facts =
            new LinkedHashSet<Class<? extends FactType<?>>>(Arrays.asList(declaredFacts));
        if (facts.contains(null)) {
            throw new NullPointerException("declaredFacts contains null");
        }
        this.declaredFacts = Collections.unmodifiableSet(facts);
    }

    /** 创建“按动作精确绑定”：仅对指定 final Action 生效，不会被兄弟动作继承。 */
    @SafeVarargs
    public static FactBinding forAction(Class<? extends ActionType> actionType,
            FactSource source, ActionFactProvider provider,
            Class<? extends FactType<?>>... declaredFacts) {
        Objects.requireNonNull(actionType, "actionType");
        int modifiers = actionType.getModifiers();
        if (actionType.isInterface() || Modifier.isAbstract(modifiers) || !Modifier.isFinal(modifiers)) {
            throw new IllegalArgumentException("Action binding target must be concrete and final");
        }
        return new FactBinding(actionType, null, source, provider, declaredFacts);
    }

    /** 创建“按契约绑定”：所有实现该契约的动作共享同一提供逻辑。 */
    @SafeVarargs
    public static FactBinding forContract(Class<? extends ActionContract> contractType,
            FactSource source, ActionFactProvider provider,
            Class<? extends FactType<?>>... declaredFacts) {
        Objects.requireNonNull(contractType, "contractType");
        if (contractType == ActionContract.class || !contractType.isInterface()) {
            throw new IllegalArgumentException("Contract binding target must be a specific contract interface");
        }
        return new FactBinding(null, contractType, source, provider, declaredFacts);
    }

    /** @return 该绑定是否覆盖给定具体 Action。 */
    public boolean appliesTo(Class<? extends ActionType> candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return actionType != null ? actionType.equals(candidate) : contractType.isAssignableFrom(candidate);
    }

    public ActionFactProvider getProvider() {
        return provider;
    }

    public FactSource getSource() {
        return source;
    }

    public Set<Class<? extends FactType<?>>> getDeclaredFacts() {
        return declaredFacts;
    }

    public Class<? extends ActionType> getActionType() {
        return actionType;
    }

    public Class<? extends ActionContract> getContractType() {
        return contractType;
    }
}
