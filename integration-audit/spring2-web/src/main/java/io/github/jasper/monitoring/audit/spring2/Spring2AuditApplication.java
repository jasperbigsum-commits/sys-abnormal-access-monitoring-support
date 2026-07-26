package io.github.jasper.monitoring.audit.spring2;

import io.github.jasper.monitoring.api.management.ManagementAuthorizer;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import javax.sql.DataSource;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** Spring Boot 2 的最小可运行审计宿主项目。 */
@SpringBootApplication
public class Spring2AuditApplication {
    /** @param args Spring Boot 启动参数 */
    public static void main(String[] args) {
        SpringApplication.run(Spring2AuditApplication.class, args);
    }

    /**
     * 提供 Starter 识别的 MyBatis 会话工厂，并在应用启动时初始化独立审计 Schema。
     *
     * @param dataSource 由 Spring Boot 配置的 H2 数据源
     * @return 供监测 Starter 注册 Mapper 的会话工厂
     * @throws Exception 数据库或 DDL 初始化失败时抛出
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        initializeSchema(dataSource);
        Configuration configuration = new Configuration(new org.apache.ibatis.mapping.Environment(
            "audit-spring2", new JdbcTransactionFactory(), dataSource));
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    /** @return 宿主管理权限边界；审计夹具只允许访问自身系统范围 */
    @Bean
    public ManagementAuthorizer managementAuthorizer() {
        return (actor, operation, resource) -> {
            if (!"audit-spring2-web".equals(actor.getSystemScope())
                || !actor.getSystemScope().equals(resource.getSystemScope())) {
                throw new SecurityException("Management scope is not authorized");
            }
        };
    }

    private static void initializeSchema(DataSource dataSource) throws Exception {
        InputStream schema = Spring2AuditApplication.class.getResourceAsStream("/db/monitoring-schema.sql");
        if (schema == null) {
            throw new IllegalStateException("Monitoring schema is unavailable");
        }
        try (Connection connection = dataSource.getConnection();
             InputStreamReader reader = new InputStreamReader(schema, StandardCharsets.UTF_8)) {
            ScriptRunner runner = new ScriptRunner(connection);
            runner.setLogWriter(null);
            runner.setErrorLogWriter(null);
            runner.runScript(reader);
        }
    }
}
