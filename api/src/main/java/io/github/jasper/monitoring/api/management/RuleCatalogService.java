package io.github.jasper.monitoring.api.management;

import io.github.jasper.monitoring.api.management.command.RuleChangeCommand;
import io.github.jasper.monitoring.api.management.model.RuleView;
import io.github.jasper.monitoring.api.management.query.RuleQuery;

/**
 * 持久化规则定义的授权管理边界。
 *
 * <p>规则变更以追加新版本方式保存，不会直接修改运行中实例已冻结的规则目录。
 * 宿主需重新发布或重启其运行时配置，持久化变更才会生效。</p>
 */
public interface RuleCatalogService {
    /** @return 当前操作者可见范围内各规则的最新持久化版本 */
    ManagementPage<RuleView> search(ManagementActor actor, RuleQuery query);
    /** @return 指定规则标识对应的最新持久化版本 */
    RuleView get(ManagementActor actor, String ruleId);
    /**
     * 追加一个经审批的新版本；期望版本过期时抛出管理冲突异常。
     *
     * <p>提交人与审批人必须均来自服务端可信认证上下文，且审批人需在同一作用域并单独通过
     * {@link ManagementOperation#RULE_APPROVE} 授权。</p>
     */
    RuleView change(ManagementActor actor, ManagementActor approver, RuleChangeCommand command);
}
