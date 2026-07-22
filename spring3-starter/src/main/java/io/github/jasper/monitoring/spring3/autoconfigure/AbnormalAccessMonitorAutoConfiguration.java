package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.IdentityContextProvider;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.ResourceScopeAuthorizer;
import io.github.jasper.monitoring.api.TrustedProxyResolver;
import io.github.jasper.monitoring.core.ControlHandler;
import io.github.jasper.monitoring.core.ControlHandlerRegistry;
import io.github.jasper.monitoring.core.DefaultRuleCatalog;
import io.github.jasper.monitoring.core.DefaultSecurityMonitor;
import io.github.jasper.monitoring.core.AlertLifecycleService;
import io.github.jasper.monitoring.core.InMemoryMonitoringRepository;
import io.github.jasper.monitoring.core.MonitoringRepository;
import io.github.jasper.monitoring.core.NotificationChannel;
import io.github.jasper.monitoring.core.ResourceAccessGuard;
import io.github.jasper.monitoring.core.SecurityMonitor;
import io.github.jasper.monitoring.mybatis.MyBatisMonitoringRepository;
import io.github.jasper.monitoring.mybatis.MyBatisMonitoringRepositoryRegistrar;
import io.github.jasper.monitoring.spring.support.ConfiguredTrustedProxyResolver;
import io.github.jasper.monitoring.spring.support.FrontendSignalRecorder;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Boot 3 ({@code jakarta.servlet}) auto-configuration for the monitoring component.
 *
 * <p>The configuration selects the MyBatis repository when a {@link SqlSessionFactory} is present,
 * otherwise it supplies an in-memory repository for local development. Host beans always take
 * precedence over defaults. Resource authorization defaults to deny and {@code ENFORCE} requires
 * at least one host {@link ControlHandler}.</p>
 */
@AutoConfiguration(afterName = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@EnableConfigurationProperties(AbnormalAccessMonitorProperties.class)
public class AbnormalAccessMonitorAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean({MonitoringRepository.class, SqlSessionFactory.class})
    public MonitoringRepository abnormalAccessMonitoringRepository() {
        return new InMemoryMonitoringRepository();
    }

    @Bean
    @ConditionalOnMissingBean(MonitoringRepository.class)
    @ConditionalOnBean(SqlSessionFactory.class)
    public MonitoringRepository abnormalAccessMyBatisMonitoringRepository(SqlSessionFactory sqlSessionFactory) {
        MyBatisMonitoringRepositoryRegistrar.register(sqlSessionFactory);
        return new MyBatisMonitoringRepository(sqlSessionFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public AlertLifecycleService abnormalAccessAlertLifecycleService(MonitoringRepository repository) {
        return new AlertLifecycleService(repository, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    public ControlHandlerRegistry abnormalAccessControlHandlerRegistry(ObjectProvider<ControlHandler> handlers) {
        List<ControlHandler> values = new ArrayList<ControlHandler>();
        for (ControlHandler handler : handlers) {
            values.add(handler);
        }
        return new ControlHandlerRegistry(values);
    }

    @Bean
    @ConditionalOnMissingBean
    public NotificationChannel abnormalAccessNotificationChannel() {
        return NotificationChannel.noop();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityMonitor.class)
    public DefaultSecurityMonitor abnormalAccessSecurityMonitor(AbnormalAccessMonitorProperties properties,
                                                                 MonitoringRepository repository,
                                                                 ControlHandlerRegistry handlers,
                                                                 NotificationChannel notifications) {
        return new DefaultSecurityMonitor(properties.getSystemId(), Clock.systemUTC(), repository,
            DefaultRuleCatalog.initialRules(), properties.getMode(), handlers, notifications);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdentityContextProvider abnormalAccessIdentityContextProvider() {
        return new IdentityContextProvider() {
            @Override
            public IdentityContext resolve(MonitoringRequestContext request) {
                return IdentityContext.anonymous();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public ResourceScopeAuthorizer abnormalAccessResourceScopeAuthorizer() {
        return (identity, request) -> AuthorizationDecision.denied("RESOURCE_SCOPE_AUTHORIZER_NOT_CONFIGURED");
    }

    @Bean
    @ConditionalOnMissingBean
    public ResourceAccessGuard abnormalAccessResourceAccessGuard(ResourceScopeAuthorizer authorizer,
                                                                   SecurityMonitor monitor) {
        return new ResourceAccessGuard(authorizer, monitor, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    public TrustedProxyResolver abnormalAccessTrustedProxyResolver(AbnormalAccessMonitorProperties properties) {
        return new ConfiguredTrustedProxyResolver(properties.getTrustedProxies());
    }

    @Bean
    @ConditionalOnMissingBean
    public FrontendSignalRecorder abnormalAccessFrontendSignalRecorder(SecurityMonitor monitor) {
        return new FrontendSignalRecorder(monitor);
    }

    @Bean("abnormalAccessRequestMetadataInterceptor")
    @ConditionalOnProperty(prefix = "abnormal.access.monitor.frontend", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RequestMetadataInterceptor abnormalAccessRequestMetadataInterceptor(TrustedProxyResolver trustedProxyResolver,
                                                                                IdentityContextProvider identityContextProvider) {
        return new RequestMetadataInterceptor(trustedProxyResolver, identityContextProvider);
    }

    @Bean("abnormalAccessMonitoringWebMvcConfigurer")
    @ConditionalOnProperty(prefix = "abnormal.access.monitor.frontend", name = "enabled", havingValue = "true", matchIfMissing = true)
    public WebMvcConfigurer abnormalAccessMonitoringWebMvcConfigurer(final RequestMetadataInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }
}
