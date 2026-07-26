package io.github.jasper.monitoring.mybatis;

import io.github.jasper.monitoring.mybatis.mapper.AlertMapper;
import io.github.jasper.monitoring.mybatis.mapper.ControlMapper;
import io.github.jasper.monitoring.mybatis.mapper.EventMapper;
import io.github.jasper.monitoring.mybatis.mapper.ManagementAuditMapper;
import io.github.jasper.monitoring.mybatis.mapper.ManagementQueryMapper;
import io.github.jasper.monitoring.mybatis.mapper.NotificationDeliveryMapper;
import io.github.jasper.monitoring.mybatis.mapper.RuleObservationMapper;
import io.github.jasper.monitoring.mybatis.mapper.WhitelistMapper;
import java.util.Objects;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;

/** Registers the mapper set required by the MyBatis monitoring stores. */
public final class MyBatisMonitoringStoreRegistrar {
    private MyBatisMonitoringStoreRegistrar() {
    }

    public static void register(SqlSessionFactory factory) {
        Objects.requireNonNull(factory, "factory");
        register(factory.getConfiguration());
    }

    public static void register(Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        configuration.getTypeHandlerRegistry().register(InstantTypeHandler.class);
        add(configuration, EventMapper.class);
        add(configuration, AlertMapper.class);
        add(configuration, ControlMapper.class);
        add(configuration, WhitelistMapper.class);
        add(configuration, NotificationDeliveryMapper.class);
        add(configuration, RuleObservationMapper.class);
        add(configuration, ManagementAuditMapper.class);
        add(configuration, ManagementQueryMapper.class);
    }

    private static void add(Configuration configuration, Class<?> mapper) {
        if (!configuration.hasMapper(mapper)) configuration.addMapper(mapper);
    }
}
