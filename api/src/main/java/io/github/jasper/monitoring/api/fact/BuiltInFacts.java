package io.github.jasper.monitoring.api.fact;

import io.github.jasper.monitoring.api.authentication.AuthenticationStage;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 内置 Fact 的标记接口，用于区分宿主自定义 Fact。 */
interface BuiltInFactType<T> extends FactType<T> {
}

/**
 * 组件内置 Fact 定义集合。
 * <p>
 * 每个 FactDefinition 都描述了：对象语义、允许来源、存储方式和取值规范，
 * 由组件在运行时初始化阶段注册进 {@link FactCatalog}。
 */
public final class BuiltInFacts {
    /**
     * 资源标识（对象：被访问/导出/授权判断的业务资源）。
     * <p>
     * 用例：
     * 报表导出填入 reportId、数据查询填入记录主键、授权决策填入目标 resourceId。
     */
    public static final FactDefinition<String> RESOURCE_ID = FactDefinition
        .builder(ResourceId.class, "resource_id", String.class)
        .allowedSources(FactSource.TRUSTED_REQUEST, FactSource.METHOD_PARAMETER,
            FactSource.HOST_PROVIDER, FactSource.CLIENT_SUPPLEMENTAL)
        .sensitivity(FactDefinition.Sensitivity.INTERNAL)
        .maxLength(256)
        .storage(FactDefinition.Storage.STANDARD_COLUMN)
        .codec(FactDefinition.stringCodec(value -> value.trim()))
        .build();

    /** Organization or tenant boundary used by host resource authorization. */
    public static final FactDefinition<String> ORG_SCOPE = FactDefinition
        .builder(OrgScope.class, "org_scope", String.class)
        .allowedSources(FactSource.TRUSTED_REQUEST, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
        .sensitivity(FactDefinition.Sensitivity.INTERNAL).maxLength(256)
        .storage(FactDefinition.Storage.STANDARD_COLUMN)
        .codec(FactDefinition.stringCodec(value -> value.trim())).build();

    /**
     * 数据量计数（对象：一次行为涉及的数据规模）。
     * <p>
     * 用例：
     * 导出记录行数、批量查询结果条数、批处理影响记录数。
     */
    public static final FactDefinition<Long> DATA_COUNT = FactDefinition
        .builder(DataCount.class, "data_count", Long.class)
        .allowedSources(FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
        .sensitivity(FactDefinition.Sensitivity.INTERNAL)
        .maxLength(20)
        .storage(FactDefinition.Storage.EXTENSION)
        .codec(FactDefinition.longCodec(value -> value))
        .validator(value -> value >= 0L)
        .build();

    /**
     * 敏感级别（对象：资源或操作的业务敏感度标签）。
     * <p>
     * 用例：
     * 标注为 low/medium/high/internal，用于区分普通查询与高敏查看风险。
     */
    public static final FactDefinition<SensitivityLevel> SENSITIVITY = FactDefinition
        .builder(Sensitivity.class, "sensitivity", SensitivityLevel.class)
        .allowedSources(FactSource.TRUSTED_REQUEST, FactSource.HOST_PROVIDER)
        .sensitivity(FactDefinition.Sensitivity.INTERNAL)
        .maxLength(64)
        .storage(FactDefinition.Storage.EXTENSION)
        .codec(FactDefinition.enumCodec(SensitivityLevel.class))
        .build();

    /** 跨网络访问标记（对象：同主体访问来源网络是否变化）。 */
    public static final FactDefinition<Boolean> DIFFERENT_NETWORKS = booleanFact(
        DifferentNetworks.class, "different_networks");
    /** 顺序访问标记（对象：资源访问路径是否呈连续遍历特征）。 */
    public static final FactDefinition<Boolean> SEQUENTIAL_ACCESS = booleanFact(
        SequentialAccess.class, "sequential_access");
    /** 敏感对象标记（对象：当前资源是否属于敏感数据对象）。 */
    public static final FactDefinition<Boolean> SENSITIVE = booleanFact(Sensitive.class, "sensitive");
    /** 工作时段标记（对象：行为发生时间是否在业务定义的工作时段内）。 */
    public static final FactDefinition<Boolean> WORK_HOURS = booleanFact(WorkHours.class, "work_hours");
    /** 提权操作标记（对象：权限变更是否导致权限上升）。 */
    public static final FactDefinition<Boolean> PRIVILEGE_INCREASE = booleanFact(
        PrivilegeIncrease.class, "privilege_increase");
    /** 高权限标记（对象：目标权限是否属于高危/高敏权限）。 */
    public static final FactDefinition<Boolean> HIGH_PRIVILEGE = booleanFact(HighPrivilege.class, "high_privilege");
    /**
     * 目标用户标识（对象：被操作或被授权变更的用户主体）。
     * <p>
     * 用例：
     * 管理员给某用户授权、禁用某账号、重置某用户安全策略时填入该用户 ID。
     */
    public static final FactDefinition<String> TARGET_USER_ID = FactDefinition
        .builder(TargetUserId.class, "target_user_id", String.class)
        .allowedSources(FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
        .sensitivity(FactDefinition.Sensitivity.INTERNAL).maxLength(128)
        .storage(FactDefinition.Storage.EXTENSION)
        .codec(FactDefinition.stringCodec(value -> value.trim())).build();
    /**
     * 基线比值（对象：当前行为相对历史基线的偏离倍率）。
     * <p>
     * 用例：
     * 当前导出量 / 用户近 30 天平均导出量，或当前查询频率 / 历史稳定频率。
     */
    public static final FactDefinition<BigDecimal> BASELINE_RATIO = FactDefinition
        .builder(BaselineRatio.class, "baseline_ratio", BigDecimal.class)
        .allowedSources(FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
        .sensitivity(FactDefinition.Sensitivity.INTERNAL).maxLength(32)
        .storage(FactDefinition.Storage.EXTENSION)
        .codec(FactDefinition.bigDecimalCodec())
        .validator(value -> value.signum() >= 0).build();

    /** Opaque, framework-derived subject used to correlate login attempts. */
    public static final FactDefinition<String> LOGIN_SUBJECT_KEY = FactDefinition
        .builder(LoginSubjectKey.class, "login_subject_key", String.class)
        .allowedSources(FactSource.FRAMEWORK_OUTCOME)
        .sensitivity(FactDefinition.Sensitivity.SENSITIVE)
        .maxLength(128)
        .storage(FactDefinition.Storage.EXTENSION)
        .codec(FactDefinition.stringCodec(value -> value.trim()))
        .validator(value -> !value.isEmpty())
        .build();

    /** Authentication checkpoint that produced the result. */
    public static final FactDefinition<AuthenticationStage> AUTHENTICATION_STAGE = FactDefinition
        .builder(AuthenticationStageFact.class, "authentication_stage", AuthenticationStage.class)
        .allowedSources(FactSource.FRAMEWORK_OUTCOME)
        .sensitivity(FactDefinition.Sensitivity.INTERNAL)
        .maxLength(16)
        .storage(FactDefinition.Storage.EXTENSION)
        .codec(FactDefinition.enumCodec(AuthenticationStage.class))
        .build();

    private static final List<FactDefinition<?>> ALL = Collections.unmodifiableList(
        Arrays.<FactDefinition<?>>asList(RESOURCE_ID, ORG_SCOPE, DATA_COUNT, SENSITIVITY, DIFFERENT_NETWORKS,
            SEQUENTIAL_ACCESS, SENSITIVE, WORK_HOURS, PRIVILEGE_INCREASE, HIGH_PRIVILEGE,
            TARGET_USER_ID, BASELINE_RATIO, LOGIN_SUBJECT_KEY, AUTHENTICATION_STAGE));

    private BuiltInFacts() {
    }

    /** @return 按稳定注册顺序返回全部内置 Fact 定义。 */
    public static List<FactDefinition<?>> all() {
        return ALL;
    }

    /** 将组件内置 Fact 定义注册到可变目录。 */
    public static void registerInto(FactCatalog catalog) {
        for (FactDefinition<?> definition : ALL) {
            catalog.register(definition);
        }
    }

    /** 资源标识 token：例如 reportId、orderId、documentId。 */
    public static final class ResourceId implements BuiltInFactType<String> {
        private ResourceId() {
        }
    }

    public static final class OrgScope implements BuiltInFactType<String> {
        private OrgScope() { }
    }

    /** 数据计数 token：例如导出行数、查询结果条数。 */
    public static final class DataCount implements BuiltInFactType<Long> {
        private DataCount() {
        }
    }

    /** 敏感级别 token：例如 low、medium、high。 */
    public static final class Sensitivity implements BuiltInFactType<SensitivityLevel> {
        private Sensitivity() {
        }
    }
    /** 内置敏感级别。 */
    public enum SensitivityLevel {
        LOW,
        MEDIUM,
        HIGH,
        INTERNAL
    }

    /** 跨网络访问 token：true 表示来源网络发生变化。 */
    public static final class DifferentNetworks implements BuiltInFactType<Boolean> { private DifferentNetworks() { } }
    /** 顺序访问 token：true 表示疑似连续枚举/遍历资源。 */
    public static final class SequentialAccess implements BuiltInFactType<Boolean> { private SequentialAccess() { } }
    /** 敏感对象 token：true 表示当前资源属于敏感对象。 */
    public static final class Sensitive implements BuiltInFactType<Boolean> { private Sensitive() { } }
    /** 工作时段 token：true 表示行为发生于工作时段。 */
    public static final class WorkHours implements BuiltInFactType<Boolean> { private WorkHours() { } }
    /** 提权操作 token：true 表示权限变更方向为上升。 */
    public static final class PrivilegeIncrease implements BuiltInFactType<Boolean> { private PrivilegeIncrease() { } }
    /** 高权限 token：true 表示变更目标为高权限集合。 */
    public static final class HighPrivilege implements BuiltInFactType<Boolean> { private HighPrivilege() { } }
    /** 目标用户 token：表示被操作的用户 ID。 */
    public static final class TargetUserId implements BuiltInFactType<String> { private TargetUserId() { } }
    /** 基线比值 token：表示当前行为相对基线的偏离倍率。 */
    public static final class BaselineRatio implements BuiltInFactType<BigDecimal> { private BaselineRatio() { } }
    /** Protected opaque login subject token. */
    public static final class LoginSubjectKey implements BuiltInFactType<String> { private LoginSubjectKey() { } }
    /** Authentication stage token. */
    public static final class AuthenticationStageFact implements BuiltInFactType<AuthenticationStage> {
        private AuthenticationStageFact() { }
    }

    /** 布尔类 Fact 的统一定义。 */
    private static <F extends FactType<Boolean>> FactDefinition<Boolean> booleanFact(Class<F> type, String key) {
        return FactDefinition.builder(type, key, Boolean.class)
            .allowedSources(FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .sensitivity(FactDefinition.Sensitivity.INTERNAL).maxLength(5)
            .storage(FactDefinition.Storage.EXTENSION)
            .codec(FactDefinition.booleanCodec()).build();
    }
}
