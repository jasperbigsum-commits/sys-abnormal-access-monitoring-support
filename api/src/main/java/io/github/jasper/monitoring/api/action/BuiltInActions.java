package io.github.jasper.monitoring.api.action;

import io.github.jasper.monitoring.api.SecurityEventType;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.fact.BuiltInFacts;
import io.github.jasper.monitoring.api.fact.FactSource;

/** 内置 Action 的标记接口，用于与宿主自定义 Action 类型区分。 */
interface BuiltInActionType extends ActionType {
}

/**
 * 组件内置 Action 类型集合。
 * <p>
 * 该类统一维护内置 Action 的 token（类型）与注册元数据（编码、事件类型、Fact 约束和失败策略），
 * 由组件在运行时初始化阶段写入 {@link ActionCatalog}。
 */
public final class BuiltInActions {
    private BuiltInActions() {
    }

    /**
     * 将内置 Contract 和 Action 注册到可变目录。
     * <p>
     * 订阅语义（按注册分层）如下：
     * <ul>
     *     <li>契约层：先注册 {@link ExportContract}，用于约束导出类行为必须具备的 Fact 与最小失败策略。</li>
     *     <li>高风险动作：如导出、权限提升，使用 FAIL_CLOSED，Fact 不满足时默认拒绝，避免漏拦截。</li>
     *     <li>常规监测动作：如查询、会话并发、访问放行/拒绝，使用 OBSERVE_ONLY，优先记录证据。</li>
     *     <li>前端补充遥测：仅接收 CLIENT_SUPPLEMENTAL 来源，不直接绑定内置规则，作为辅助信号。</li>
     * </ul>
     * 调用方应在完成注册后对目录执行 freeze，确保运行期订阅与语义不可变。
     */
    public static void registerInto(ActionCatalog catalog) {
        catalog.registerContract(AuthenticationContract.class, ActionContractDefinition.builder()
            .minimumFailurePolicy(ActionFailurePolicy.OBSERVE_ONLY)
            .build());
        // ExportContract：导出类统一契约。对象=导出行为；用例=报表导出、批量数据导出前统一校验资源ID和数据量。
        catalog.registerContract(ExportContract.class, ActionContractDefinition.builder()
            .require(BuiltInFacts.ResourceId.class,
                FactSource.TRUSTED_REQUEST, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.OrgScope.class, FactSource.HOST_PROVIDER)
            .require(BuiltInFacts.DataCount.class,
                FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .minimumFailurePolicy(ActionFailurePolicy.FAIL_CLOSED)
            .build());
        // report:export：报表导出动作。对象=报表资源导出；用例=用户导出订单/财务报表，评估敏感度和基线偏离。
        catalog.register(ReportExport.class, ActionDefinition.builder("report:export")
            .eventType(SecurityEventType.EXPORT)
            .resourceType("report")
            .ruleTag("export")
            .optional(BuiltInFacts.Sensitivity.class, FactSource.TRUSTED_REQUEST, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.BaselineRatio.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.FAIL_CLOSED)
            .build());
        // auth:login：统一登录动作。最终结果决定 LOGIN_SUCCESS 或 LOGIN_FAILURE 事件类型。
        catalog.register(Login.class, ActionDefinition.builder("auth:login")
            .eventType(SecurityEventType.LOGIN_FAILURE)
            .eventTypeFor(SecurityEventResult.SUCCESS, SecurityEventType.LOGIN_SUCCESS)
            .resourceType("authentication")
            .require(BuiltInFacts.LoginSubjectKey.class, FactSource.FRAMEWORK_OUTCOME)
            .require(BuiltInFacts.AuthenticationStageFact.class, FactSource.FRAMEWORK_OUTCOME)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY)
            .build());
        // data:query：普通查询动作。对象=业务数据读取；用例=详情查询、列表检索、按条件筛选查询。
        catalog.register(Query.class, ActionDefinition.builder("data:query")
            .eventType(SecurityEventType.QUERY).resourceType("resource")
            .optional(BuiltInFacts.ResourceId.class, FactSource.TRUSTED_REQUEST,
                FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.OrgScope.class, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.SequentialAccess.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build());
        // session:concurrent：会话并发动作。对象=同主体多会话访问；用例=同账号在不同终端/网络短时并发操作。
        catalog.register(SessionConcurrent.class, ActionDefinition.builder("session:concurrent")
            .eventType(SecurityEventType.SESSION_CONCURRENT).resourceType("monitoring")
            .optional(BuiltInFacts.DataCount.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.DifferentNetworks.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build());
        // authz:access-denied：授权拒绝动作。对象=资源访问被拒；用例=越权访问、跨租户访问、无权限读取。
        catalog.register(AccessDenied.class, access("authz:access-denied", SecurityEventType.ACCESS_DENIED));
        // authz:access-allowed：授权放行动作。对象=资源访问被允许；用例=关键资源访问放行留痕，用于“允许但异常”分析。
        catalog.register(AccessAllowed.class, access("authz:access-allowed", SecurityEventType.ACCESS_ALLOWED));
        // privilege:change：权限变更动作。对象=主体权限调整；用例=管理员授予角色、提升操作权限、临时开通高权。
        catalog.register(PrivilegeChange.class, ActionDefinition.builder("privilege:change")
            .eventType(SecurityEventType.ROLE_GRANT).resourceType("monitoring")
            .require(BuiltInFacts.TargetUserId.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .require(BuiltInFacts.PrivilegeIncrease.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.HighPrivilege.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.FAIL_CLOSED).build());
        // security:configuration-change：安全配置变更动作。对象=安全策略/规则调整；用例=规则启停、阈值修改、控制策略切换。
        catalog.register(SecurityChange.class, action("security:configuration-change", SecurityEventType.RULE_CHANGE));
        // resource:view-sensitive：敏感资源查看动作。对象=敏感数据读取；用例=客户隐私信息查看、核心报表预览、敏感字段检索。
        catalog.register(SensitiveView.class, ActionDefinition.builder("resource:view-sensitive")
            .eventType(SecurityEventType.VIEW_SENSITIVE)
            .resourceType("resource")
            .ruleTag("sensitive")
            .optional(BuiltInFacts.Sensitivity.class,
                FactSource.TRUSTED_REQUEST, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.ResourceId.class, FactSource.TRUSTED_REQUEST,
                FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.DataCount.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.Sensitive.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.WorkHours.class, FactSource.METHOD_PARAMETER, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY)
            .build());
        // frontend:signal：前端补充遥测动作。对象=浏览器侧补充信号；用例=页面路径、交互上下文补充，不参与内置强约束决策。
        catalog.register(FrontendSignal.class, ActionDefinition.builder("frontend:signal")
            .eventType(SecurityEventType.QUERY).resourceType("frontend-route")
            .optional(BuiltInFacts.ResourceId.class, FactSource.CLIENT_SUPPLEMENTAL)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build());
    }

    /** 监测域通用事件（monitoring 资源类型）的便捷定义。 */
    private static ActionDefinition action(String code, SecurityEventType eventType) {
        return ActionDefinition.builder(code).eventType(eventType).resourceType("monitoring")
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
    }

    /** 资源访问事件（resource 资源类型）的便捷定义。 */
    private static ActionDefinition access(String code, SecurityEventType eventType) {
        return ActionDefinition.builder(code).eventType(eventType).resourceType("resource")
            .optional(BuiltInFacts.ResourceId.class, FactSource.TRUSTED_REQUEST, FactSource.HOST_PROVIDER)
            .optional(BuiltInFacts.OrgScope.class, FactSource.TRUSTED_REQUEST, FactSource.HOST_PROVIDER)
            .failurePolicy(ActionFailurePolicy.OBSERVE_ONLY).build();
    }

    /** 导出类行为共享语义契约：统一要求资源标识与数据规模等最小事实集。 */
    public interface ExportContract extends ActionContract {
    }

    /** Authentication actions share identity/attempted-subject semantics. */
    public interface AuthenticationContract extends ActionContract {
    }

    /** 内置报表导出行为；用例：导出订单、财务、审计报表。 */
    public static final class ReportExport implements BuiltInActionType, ExportContract {
        private ReportExport() {
        }
    }

    /** 内置敏感资源查看行为；用例：查看隐私信息、敏感字段、受限文档。 */
    public static final class SensitiveView implements BuiltInActionType {
        private SensitiveView() {
        }
    }
    /** Unified login behavior; outcome maps to success or failure event types. */
    public static final class Login implements BuiltInActionType, AuthenticationContract { private Login() { } }
    /** 普通查询行为；用例：列表查询、检索、按条件读取业务数据。 */
    public static final class Query implements BuiltInActionType { private Query() { } }
    /** 会话并发行为；用例：同账号在多终端/多IP同时操作。 */
    public static final class SessionConcurrent implements BuiltInActionType { private SessionConcurrent() { } }
    /** 授权拒绝行为；用例：越权访问被业务授权层拒绝。 */
    public static final class AccessDenied implements BuiltInActionType { private AccessDenied() { } }
    /** 授权放行行为；用例：高敏资源访问通过后进行留痕分析。 */
    public static final class AccessAllowed implements BuiltInActionType { private AccessAllowed() { } }
    /** 权限变更行为；用例：角色授予、权限提级、临时授权。 */
    public static final class PrivilegeChange implements BuiltInActionType { private PrivilegeChange() { } }
    /** 安全配置变更行为；用例：规则阈值调整、策略启停、控制模式切换。 */
    public static final class SecurityChange implements BuiltInActionType { private SecurityChange() { } }
    /** 前端补充遥测；用例：补充页面路由/交互上下文，刻意不绑定内置规则。 */
    public static final class FrontendSignal implements BuiltInActionType { private FrontendSignal() { } }
}
