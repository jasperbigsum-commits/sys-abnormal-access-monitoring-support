package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.IdentityContextProvider;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.ResourceScopeAuthorizer;
import io.github.jasper.monitoring.api.TrustedProxyResolver;
import io.github.jasper.monitoring.core.application.ActionEventRecorder;
import io.github.jasper.monitoring.core.application.control.AnnotatedControlHandler;
import io.github.jasper.monitoring.core.application.control.DefaultControlActionTrigger;
import io.github.jasper.monitoring.core.port.ControlHandler;
import io.github.jasper.monitoring.core.application.control.ControlHandlerRegistry;
import io.github.jasper.monitoring.core.domain.rule.DefaultRuleCatalog;
import io.github.jasper.monitoring.core.application.DefaultSecurityMonitor;
import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import io.github.jasper.monitoring.core.application.AlertLifecycleService;
import io.github.jasper.monitoring.core.infrastructure.memory.InMemoryMonitoringRepository;
import io.github.jasper.monitoring.core.application.rule.InternalRuleContributor;
import io.github.jasper.monitoring.core.application.rule.InternalRuleRegistry;
import io.github.jasper.monitoring.core.application.MonitoringActionRegistry;
import io.github.jasper.monitoring.core.port.MonitoringRepository;
import io.github.jasper.monitoring.core.port.NotificationChannel;
import io.github.jasper.monitoring.core.application.authorization.ResourceAccessGuard;
import io.github.jasper.monitoring.core.application.SecurityMonitor;
import io.github.jasper.monitoring.mybatis.MyBatisMonitoringRepository;
import io.github.jasper.monitoring.mybatis.MyBatisMonitoringRepositoryRegistrar;
import io.github.jasper.monitoring.spring.support.ConfiguredTrustedProxyResolver;
import io.github.jasper.monitoring.spring.support.FrontendSignalRecorder;
import io.github.jasper.monitoring.spring.support.MdcTraceBridge;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

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
    /** 当没有数据源会话工厂时提供仅用于本地开发和测试的内存仓储。 */
    @Bean
    @ConditionalOnMissingBean({MonitoringRepository.class, SqlSessionFactory.class})
    public MonitoringRepository abnormalAccessMonitoringRepository() {
        return new InMemoryMonitoringRepository();
    }

    /** 当宿主提供 MyBatis 会话工厂时注册 Mapper 并创建生产仓储。 */
    @Bean
    @ConditionalOnMissingBean(MonitoringRepository.class)
    @ConditionalOnBean(SqlSessionFactory.class)
    public MonitoringRepository abnormalAccessMyBatisMonitoringRepository(SqlSessionFactory sqlSessionFactory) {
        MyBatisMonitoringRepositoryRegistrar.register(sqlSessionFactory);
        return new MyBatisMonitoringRepository(sqlSessionFactory);
    }

    /** 创建告警处置生命周期服务。 */
    @Bean
    @ConditionalOnMissingBean
    public AlertLifecycleService abnormalAccessAlertLifecycleService(MonitoringRepository repository) {
        return new AlertLifecycleService(repository, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    public ControlHandlerRegistry abnormalAccessControlHandlerRegistry(ObjectProvider<ControlHandler> handlers,
                                                                        ListableBeanFactory beanFactory) {
        List<ControlHandler> values = new ArrayList<ControlHandler>();
        for (ControlHandler handler : handlers) {
            values.add(handler);
        }
        Set<ControlActionType> annotatedActions = EnumSet.noneOf(ControlActionType.class);
        for (String beanName : beanFactory.getBeanNamesForType(Object.class, false, false)) {
            Class<?> type = beanFactory.getType(beanName);
            if (type == null || ControlHandler.class.isAssignableFrom(type)
                || !AnnotatedControlHandler.hasBindings(type)) {
                continue;
            }
            ControlHandler handler = AnnotatedControlHandler.lazy(type, () -> beanFactory.getBean(beanName));
            for (ControlActionType action : ControlActionType.values()) {
                if (handler.supports(action) && !annotatedActions.add(action)) {
                    throw new IllegalArgumentException("Duplicate ControlTrigger binding for " + action);
                }
            }
            values.add(handler);
        }
        return new ControlHandlerRegistry(values, DefaultControlActionTrigger.defaults());
    }

    @Bean
    @ConditionalOnMissingBean
    public NotificationChannel abnormalAccessNotificationChannel() {
        return NotificationChannel.noop();
    }

    /**
     * 创建内部代码规则注册器，收集基线规则、{@link DetectionRule} Bean 和规则贡献者。
     * 注册器将在监测器创建前冻结，数据库规则配置不会修改这份运行期快照。
     */
    @Bean
    @ConditionalOnMissingBean
    public InternalRuleRegistry abnormalAccessInternalRuleRegistry(ObjectProvider<DetectionRule> rules,
                                                                    ObjectProvider<InternalRuleContributor> contributors) {
        InternalRuleRegistry registry = new InternalRuleRegistry(DefaultRuleCatalog.initialRules());
        for (DetectionRule rule : rules) {
            registry.register(rule);
        }
        for (InternalRuleContributor contributor : contributors) {
            contributor.register(registry);
        }
        return registry;
    }

    /** 创建手工埋点动作的启动期注册表；宿主可用同类型 Bean 覆盖。 */
    @Bean
    @ConditionalOnMissingBean
    public MonitoringActionRegistry abnormalAccessMonitoringActionRegistry() {
        return new MonitoringActionRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityMonitor.class)
    public DefaultSecurityMonitor abnormalAccessSecurityMonitor(AbnormalAccessMonitorProperties properties,
                                                                 MonitoringRepository repository,
                                                                 ControlHandlerRegistry handlers,
                                                                 NotificationChannel notifications,
                                                                 InternalRuleRegistry rules) {
        return new DefaultSecurityMonitor(properties.getSystemId(), Clock.systemUTC(), repository,
            rules.freeze().rules(), properties.getMode(), handlers, notifications);
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

    /** 创建可选日志 MDC 链路追踪桥接器；没有 SLF4J MDC 时自动退化为无操作。 */
    @Bean
    @ConditionalOnMissingBean
    public MdcTraceBridge abnormalAccessMdcTraceBridge(AbnormalAccessMonitorProperties properties) {
        return MdcTraceBridge.create(properties.getMdc().isEnabled(), properties.getMdc().getTraceIdKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionEventRecorder abnormalAccessActionEventRecorder(SecurityMonitor monitor,
                                                                  MonitoringActionRegistry actions) {
        return new ActionEventRecorder(monitor, Clock.systemUTC(), actions);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = {"org.springframework.web.servlet.HandlerInterceptor", "org.aspectj.lang.annotation.Aspect"})
    @ConditionalOnProperty(prefix = "abnormal.access.monitor.instrumentation", name = "enabled", havingValue = "true", matchIfMissing = true)
    @EnableAspectJAutoProxy
    static class MvcInstrumentationConfiguration {
        @Bean("abnormalAccessAnnotatedActionMonitoringAspect")
        @ConditionalOnMissingBean(AnnotatedActionMonitoringAspect.class)
        AnnotatedActionMonitoringAspect abnormalAccessAnnotatedActionMonitoringAspect(ListableBeanFactory beanFactory) {
            return new AnnotatedActionMonitoringAspect(beanFactory);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.HandlerInterceptor")
    static class ServletMvcConfiguration {
        @Bean("abnormalAccessRequestMetadataInterceptor")
        RequestMetadataInterceptor abnormalAccessRequestMetadataInterceptor(TrustedProxyResolver trustedProxyResolver,
                                                                            IdentityContextProvider identityContextProvider,
                                                                            MdcTraceBridge mdcTraceBridge) {
            return new RequestMetadataInterceptor(trustedProxyResolver, identityContextProvider, mdcTraceBridge);
        }

        @Bean("abnormalAccessAnnotatedActionMonitoringInterceptor")
        @ConditionalOnMissingBean(AnnotatedActionMonitoringInterceptor.class)
        @ConditionalOnProperty(prefix = "abnormal.access.monitor.instrumentation", name = "enabled", havingValue = "true", matchIfMissing = true)
        AnnotatedActionMonitoringInterceptor abnormalAccessAnnotatedActionMonitoringInterceptor(
            ActionEventRecorder recorder, TrustedProxyResolver trustedProxyResolver,
            IdentityContextProvider identityContextProvider, MdcTraceBridge mdcTraceBridge) {
            return new AnnotatedActionMonitoringInterceptor(recorder, trustedProxyResolver, identityContextProvider,
                mdcTraceBridge);
        }

        @Bean("abnormalAccessMonitoringWebMvcConfigurer")
        org.springframework.web.servlet.config.annotation.WebMvcConfigurer abnormalAccessMonitoringWebMvcConfigurer(
            final RequestMetadataInterceptor interceptor) {
            return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer() {
                @Override
                public void addInterceptors(
                    org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
                    registry.addInterceptor(interceptor).order(0);
                }
            };
        }

        @Bean("abnormalAccessAnnotatedActionWebMvcConfigurer")
        @ConditionalOnProperty(prefix = "abnormal.access.monitor.instrumentation", name = "enabled", havingValue = "true", matchIfMissing = true)
        org.springframework.web.servlet.config.annotation.WebMvcConfigurer abnormalAccessAnnotatedActionWebMvcConfigurer(
            final AnnotatedActionMonitoringInterceptor interceptor) {
            return new org.springframework.web.servlet.config.annotation.WebMvcConfigurer() {
                @Override
                public void addInterceptors(
                    org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
                    registry.addInterceptor(interceptor).order(1);
                }
            };
        }
    }

}
