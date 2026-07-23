package io.github.jasper.monitoring.mybatis.po;
import io.github.jasper.monitoring.api.RiskLevel;
import io.github.jasper.monitoring.api.RuleManagementEntry;
import io.github.jasper.monitoring.api.RuleMode;
import io.github.jasper.monitoring.api.RuleSource;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 数据库规则版本的管理查询 DTO。
 *
 * <p>字段由 MyBatis 映射，Lombok 仅在编译期生成访问器，不成为运行时依赖。该对象表示可动态管理的
 * 持久化配置；它不会仅因被查询或启用就自动变成 {@code DetectionRule}，运行时加载需要宿主显式批准。</p>
 */
@Getter
@Setter
public final class PersistedRuleDefinition implements RuleManagementEntry {
    /** 稳定规则标识。 */
    private String ruleId;
    /** 规则定义版本号。 */
    private int ruleVersion;
    /** 用于管理端展示的规则名称。 */
    private String ruleName;
    /** 经审批保存的规则定义内容。 */
    private String ruleDefinition;
    /** 该版本声明的风险等级。 */
    private RiskLevel riskLevel;
    /** 该版本声明的运行模式。 */
    private RuleMode ruleMode;
    /** 是否已在管理层启用该版本。 */
    private boolean enabled;
    /** 版本创建时间。 */
    private Instant createdAt;
    /** 创建或发布该版本的操作人。 */
    private String createdBy;

    /** @return {@link RuleSource#PERSISTED} */
    @Override
    public RuleSource getSource() {
        return RuleSource.PERSISTED;
    }

    /** @return {@code true}；管理端可创建新版本或切换该版本启停状态 */
    @Override
    public boolean isMutable() {
        return true;
    }
}
