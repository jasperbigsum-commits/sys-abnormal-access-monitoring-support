package io.github.jasper.monitoring.mybatis;

import java.util.Objects;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import io.github.jasper.monitoring.mybatis.mapper.EventMapper;
import io.github.jasper.monitoring.mybatis.mapper.AlertMapper;
import io.github.jasper.monitoring.mybatis.mapper.ControlMapper;
import io.github.jasper.monitoring.mybatis.mapper.WhitelistMapper;
import io.github.jasper.monitoring.mybatis.mapper.NotificationDeliveryMapper;
import io.github.jasper.monitoring.mybatis.mapper.ManagementAuditMapper;
import io.github.jasper.monitoring.mybatis.mapper.ManagementQueryMapper;

/**
 * Registers this module without mapper scanning or a framework dependency.
 * Register before opening sessions that need the monitoring or administration mappers.
 */
public final class MyBatisMonitoringRepositoryRegistrar {
    private MyBatisMonitoringRepositoryRegistrar() {
    }

    /**
     * Registers the type handler and mappers with an existing session factory.
     *
     * @param sqlSessionFactory factory to configure
     */
    public static void register(SqlSessionFactory sqlSessionFactory) {
        Objects.requireNonNull(sqlSessionFactory, "sqlSessionFactory");
        register(sqlSessionFactory.getConfiguration());
    }

    /**
     * Registers the type handler and mappers with a MyBatis configuration.
     * Repeated calls are safe and do not add duplicate mappers.
     *
     * @param configuration MyBatis configuration to extend
     */
    public static void register(Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        configuration.getTypeHandlerRegistry().register(InstantTypeHandler.class);
        if (!configuration.hasMapper(MonitoringSqlMapper.class)) {
            configuration.addMapper(MonitoringSqlMapper.class);
        }
        if (!configuration.hasMapper(MonitoringAdministrationMapper.class)) {
            configuration.addMapper(MonitoringAdministrationMapper.class);
        }
        if (!configuration.hasMapper(EventMapper.class)) configuration.addMapper(EventMapper.class);
        if (!configuration.hasMapper(AlertMapper.class)) configuration.addMapper(AlertMapper.class);
        if (!configuration.hasMapper(ControlMapper.class)) configuration.addMapper(ControlMapper.class);
        if (!configuration.hasMapper(WhitelistMapper.class)) configuration.addMapper(WhitelistMapper.class);
        if (!configuration.hasMapper(NotificationDeliveryMapper.class)) configuration.addMapper(NotificationDeliveryMapper.class);
        if (!configuration.hasMapper(ManagementAuditMapper.class)) configuration.addMapper(ManagementAuditMapper.class);
        if (!configuration.hasMapper(ManagementQueryMapper.class)) configuration.addMapper(ManagementQueryMapper.class);
    }

    /**
     * Builds a repository from a preconfigured MyBatis configuration.
     *
     * @param configuration configuration with a usable environment and data source
     * @return repository backed by a newly built session factory
     */
    public static MyBatisMonitoringRepository create(Configuration configuration) {
        register(configuration);
        return new MyBatisMonitoringRepository(new SqlSessionFactoryBuilder().build(configuration));
    }
}
