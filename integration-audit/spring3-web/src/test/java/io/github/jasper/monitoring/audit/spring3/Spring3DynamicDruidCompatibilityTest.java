package io.github.jasper.monitoring.audit.spring3;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.mybatis.repository.MyBatisMonitoringStore;
import io.github.jasper.monitoring.spring3.autoconfigure.AbnormalAccessMonitorProperties;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 验证监测 Starter 与 dynamic-datasource 的 Boot 3 Druid 多数据源装配兼容性。 */
@SpringBootTest(properties = {
    "spring.datasource.dynamic.enabled=true",
    "spring.datasource.dynamic.primary=monitoring",
    "spring.datasource.dynamic.strict=true",
    "spring.datasource.dynamic.druid.initial-size=1",
    "spring.datasource.dynamic.druid.min-idle=1",
    "spring.datasource.dynamic.druid.max-active=4",
    "spring.datasource.dynamic.druid.validation-query=SELECT 1",
    "spring.datasource.dynamic.datasource.monitoring.url=jdbc:h2:mem:audit-spring3-dynamic-monitoring;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.dynamic.datasource.monitoring.username=sa",
    "spring.datasource.dynamic.datasource.monitoring.password=",
    "spring.datasource.dynamic.datasource.monitoring.driver-class-name=org.h2.Driver",
    "spring.datasource.dynamic.datasource.monitoring.type=com.alibaba.druid.pool.DruidDataSource",
    "spring.datasource.dynamic.datasource.business.url=jdbc:h2:mem:audit-spring3-dynamic-business;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.dynamic.datasource.business.username=sa",
    "spring.datasource.dynamic.datasource.business.password=",
    "spring.datasource.dynamic.datasource.business.driver-class-name=org.h2.Driver",
    "spring.datasource.dynamic.datasource.business.type=com.alibaba.druid.pool.DruidDataSource",
    "abnormal.access.monitor.system-id=dynamic-druid-audit",
    "abnormal.access.monitor.notification.retry-enabled=false"
})
class Spring3DynamicDruidCompatibilityTest {
    @Autowired private AbnormalAccessMonitorProperties properties;
    @Autowired private DataSource dataSource;
    @Autowired private SqlSessionFactory sqlSessionFactory;
    @Autowired private MyBatisMonitoringStore monitoringStore;
    @Autowired private MonitoringService monitoringService;

    @Test
    void bindsMonitoringPropertiesAlongsideTwoDynamicDruidDataSources() throws Exception {
        assertThat(properties).isNotNull();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getSystemId()).isEqualTo("dynamic-druid-audit");
        assertThat(dataSource).isInstanceOf(DynamicRoutingDataSource.class);

        DynamicRoutingDataSource routing = (DynamicRoutingDataSource) dataSource;
        Map<String, DataSource> sources = routing.getDataSources();
        assertThat(sources).containsKeys("monitoring", "business", "master");
        assertThat(sources.values()).allSatisfy(source -> {
            assertThat(source).isInstanceOf(ItemDataSource.class);
            assertThat(((ItemDataSource) source).getRealDataSource()).isInstanceOf(DruidDataSource.class);
        });
        assertDruidSettings(sources.get("monitoring"));
        assertDruidSettings(sources.get("business"));
        assertThat(sqlSessionFactory.getConfiguration().getEnvironment().getDataSource()).isSameAs(routing);
        assertThat(monitoringStore).isNotNull();
        assertThat(monitoringService).isNotNull();

        for (DataSource source : sources.values()) {
            try (Connection connection = source.getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT 1")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        }
    }

    private void assertDruidSettings(DataSource source) {
        DruidDataSource druid = (DruidDataSource) ((ItemDataSource) source).getRealDataSource();
        assertThat(druid.getInitialSize()).isEqualTo(1);
        assertThat(druid.getMinIdle()).isEqualTo(1);
        assertThat(druid.getMaxActive()).isEqualTo(4);
        assertThat(druid.getValidationQuery()).isEqualTo("SELECT 1");
    }
}
