package io.github.jasper.monitoring.audit.spring3.persistence;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 在 MyBatis Mapper 可用后装载确定性的虚构验收数据。
 *
 * <p>数据覆盖账号、角色、报告、报告行和会话等跨包用例，仅服务测试初始化，不代表生产默认账号、
 * 权限或业务数据。</p>
 */
@Component
public final class AuditFixtureDataInitializer implements InitializingBean {
    private final AuditFixtureRepository fixtures;
    public AuditFixtureDataInitializer(AuditFixtureRepository fixtures) { this.fixtures = fixtures; }
    @Override public void afterPropertiesSet() { fixtures.seed(); }
}
