package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.EventEnricher;
import io.github.jasper.monitoring.api.IdentityContextProvider;
import io.github.jasper.monitoring.api.MonitoringContextAccessor;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.AuthorizationDecision;
import io.github.jasper.monitoring.api.ControlActionType;
import io.github.jasper.monitoring.api.ResourceScopeAuthorizer;
import io.github.jasper.monitoring.api.TrustedProxyResolver;
import io.github.jasper.monitoring.api.action.ActionCatalog;
import io.github.jasper.monitoring.api.action.BuiltInActions;
import io.github.jasper.monitoring.api.control.ControlCatalog;
import io.github.jasper.monitoring.api.control.ControlType;
import io.github.jasper.monitoring.api.fact.FactBinding;
import io.github.jasper.monitoring.api.error.MonitoringConfigurationException;
import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.core.application.ActionEventRecorder;
import io.github.jasper.monitoring.core.application.DefaultMonitoringRuntime;
import io.github.jasper.monitoring.core.application.MonitoringRuntimePort;
import io.github.jasper.monitoring.core.application.MonitoringService;
import io.github.jasper.monitoring.core.application.SecurityEventAssembler;
import io.github.jasper.monitoring.core.application.TypedRuleEvaluationService;
import io.github.jasper.monitoring.core.application.control.AnnotatedControlHandler;
import io.github.jasper.monitoring.core.application.control.DefaultControlActionTrigger;
import io.github.jasper.monitoring.core.port.ControlHandler;
import io.github.jasper.monitoring.core.application.control.ControlHandlerRegistry;
import io.github.jasper.monitoring.core.application.control.ControlExecutionService;
import io.github.jasper.monitoring.core.domain.rule.DefaultRuleCatalog;
import io.github.jasper.monitoring.core.application.DefaultSecurityMonitor;
import io.github.jasper.monitoring.core.domain.rule.DetectionRule;
import io.github.jasper.monitoring.core.application.AlertLifecycleService;
import io.github.jasper.monitoring.core.application.rule.InternalRuleContributor;
import io.github.jasper.monitoring.core.application.rule.InternalRuleRegistry;
import io.github.jasper.monitoring.core.application.MonitoringActionRegistry;
import io.github.jasper.monitoring.core.port.MonitoringRepository;
import io.github.jasper.monitoring.core.port.NotificationChannel;
import io.github.jasper.monitoring.core.application.authorization.ResourceAccessGuard;
import io.github.jasper.monitoring.core.application.SecurityMonitor;
import io.github.jasper.monitoring.mybatis.MyBatisMonitoringRepository;
import io.github.jasper.monitoring.mybatis.MyBatisMonitoringRepositoryRegistrar;
import io.github.jasper.monitoring.mybatis.repository.MyBatisControlExecutionStore;
import io.github.jasper.monitoring.mybatis.repository.MyBatisMonitoringStore;
import io.github.jasper.monitoring.spring.support.ConfiguredTrustedProxyResolver;
import io.github.jasper.monitoring.spring.support.FrontendSignalRecorder;
import io.github.jasper.monitoring.spring.support.MdcTraceBridge;
import io.github.jasper.monitoring.spring.support.control.GenericIpControlHandler;
import io.github.jasper.monitoring.spring.support.control.IpControlState;
import io.github.jasper.monitoring.spring.support.control.LocalIpControlState;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
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
 * <p>The configuration requires MyBatis-backed monitoring persistence. Host beans always take
 * precedence over defaults. Resource authorization defaults to deny and {@code ENFORCE} requires
 * at least one host {@link ControlHandler}.</p>
 */
@AutoConfiguration(afterName = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@EnableConfigurationProperties(AbnormalAccessMonitorProperties.class)
public class AbnormalAccessMonitorAutoConfiguration {
    /** 当宿主提供 MyBatis 会话工厂时注册 Mapper 并创建生产仓储。 */
    @Bean
    @ConditionalOnMissingBean(MonitoringRepository.class)
    @ConditionalOnBean(SqlSessionFactory.class)
    public MonitoringRepository abnormalAccessMyBatisMonitoringRepository(SqlSessionFactory sqlSessionFactory) {
        MyBatisMonitoringRepositoryRegistrar.register(sqlSessionFactory);
        return new MyBatisMonitoringRepository(sqlSessionFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SqlSessionFactory.class)
    public MyBatisMonitoringStore abnormalAccessMyBatisMonitoringStore(SqlSessionFactory sqlSessionFactory) {
        return new MyBatisMonitoringStore(sqlSessionFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(SqlSessionFactory.class)
    public MyBatisControlExecutionStore abnormalAccessMyBatisControlExecutionStore(
            SqlSessionFactory sqlSessionFactory) {
        return new MyBatisControlExecutionStore(sqlSessionFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionCatalog abnormalAccessActionCatalog() {
        ActionCatalog catalog = new ActionCatalog();
        BuiltInActions.registerInto(catalog);
        catalog.freeze();
        return catalog;
    }

    @Bean
    @ConditionalOnMissingBean(MonitoringRuntimePort.class)
    public DefaultMonitoringRuntime abnormalAccessMonitoringRuntime(ActionCatalog catalog,
            ObjectProvider<FactBinding> bindings) {
        List<FactBinding> values = new ArrayList<FactBinding>();
        for (FactBinding binding : bindings) values.add(binding);
        return new DefaultMonitoringRuntime(catalog, values);
    }

    @Bean
    @ConditionalOnMissingBean
    public ControlCatalog<ControlHandler> abnormalAccessControlCatalog(ControlHandlerRegistry handlers) {
        ControlCatalog.Builder<ControlHandler> catalog = ControlCatalog.builder();
        for (ControlType type : ControlType.values()) {
            java.util.Optional<ControlHandler> handler = handlers.find(ControlActionType.valueOf(type.name()));
            if (handler.isPresent()) catalog.bind(type, handler.get());
        }
        return catalog.freeze();
    }

    @Bean
    @ConditionalOnMissingBean
    public ControlExecutionService abnormalAccessControlExecutionService(
            MyBatisControlExecutionStore store, ControlCatalog<ControlHandler> catalog) {
        return new ControlExecutionService(store, catalog, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean(MonitoringService.RuleEvaluationPort.class)
    public TypedRuleEvaluationService abnormalAccessTypedRuleEvaluationService(
            MyBatisMonitoringStore store, AbnormalAccessMonitorProperties properties,
            ControlExecutionService controls, NotificationChannel notifications) {
        return new TypedRuleEvaluationService(store, store, store, store, DefaultRuleCatalog.typedRules(),
            properties.getMode(), controls, notifications, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    public MonitoringService abnormalAccessMonitoringService(AbnormalAccessMonitorProperties properties,
            MyBatisMonitoringStore store, MonitoringRuntimePort runtime,
            MonitoringService.RuleEvaluationPort evaluator) {
        return new MonitoringService(store, new SecurityEventAssembler(properties.getSystemId(), Clock.systemUTC()),
            runtime, evaluator);
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
                                                                        ObjectProvider<GenericIpControlHandler> genericHandlers,
                                                                        ListableBeanFactory beanFactory) {
        List<ControlHandler> values = new ArrayList<ControlHandler>();
        for (ControlHandler handler : handlers) {
            if (!(handler instanceof GenericIpControlHandler)) {
                values.add(handler);
            }
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
                    throw new MonitoringConfigurationException(MonitoringErrorCode.DUPLICATE_CONTROL_BINDING,
                        "Duplicate annotated ControlTrigger binding");
                }
            }
            values.add(handler);
        }
        List<ControlHandler> genericValues = new ArrayList<ControlHandler>();
        for (GenericIpControlHandler handler : genericHandlers) {
            genericValues.add(handler);
        }
        return new ControlHandlerRegistry(values, genericValues, DefaultControlActionTrigger.defaults());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
    @ConditionalOnProperty(prefix = "abnormal.access.monitor.ip-control", name = "enabled", havingValue = "true")
    static class GenericIpControlConfiguration {
        GenericIpControlConfiguration(AbnormalAccessMonitorProperties properties) {
            validate(properties);
        }

        @Bean
        @ConditionalOnMissingBean(IpControlState.class)
        LocalIpControlState abnormalAccessLocalIpControlState(AbnormalAccessMonitorProperties properties) {
            AbnormalAccessMonitorProperties.IpControl config = properties.getIpControl();
            return new LocalIpControlState(config.getCapacity(), config.getPermitsPerWindow(), config.getWindow());
        }

        @Bean
        @ConditionalOnMissingBean(GenericIpControlHandler.class)
        GenericIpControlHandler abnormalAccessGenericIpControlHandler(IpControlState state,
                                                                       AbnormalAccessMonitorProperties properties) {
            AbnormalAccessMonitorProperties.IpControl config = properties.getIpControl();
            return new GenericIpControlHandler(state, new HashSet<String>(config.getRuleIds()),
                config.getMaxTtl(), Clock.systemUTC());
        }

        @Bean
        @ConditionalOnMissingBean(IpControlFilter.class)
        IpControlFilter abnormalAccessIpControlFilter(IpControlState state,
                                                       TrustedProxyResolver trustedProxyResolver,
                                                       AbnormalAccessMonitorProperties properties) {
            AbnormalAccessMonitorProperties.IpControl config = properties.getIpControl();
            return new IpControlFilter(state, trustedProxyResolver, config.getProtectedPaths(),
                config.getExcludedPaths(), Clock.systemUTC());
        }

        private static void validate(AbnormalAccessMonitorProperties properties) {
            if (properties.getMode() != io.github.jasper.monitoring.api.MonitoringMode.ENFORCE) {
                throw invalidIpControlConfiguration("ip-control requires abnormal.access.monitor.mode=ENFORCE");
            }
            AbnormalAccessMonitorProperties.IpControl config = properties.getIpControl();
            requireNonEmptyText(config.getProtectedPaths(), "protected-paths");
            requireTextEntries(config.getExcludedPaths(), "excluded-paths");
            requireNonEmptyText(config.getRuleIds(), "rule-ids");
            if (config.getPermitsPerWindow() <= 0) {
                throw invalidIpControlConfiguration("ip-control permits-per-window must be positive");
            }
            if (config.getWindow() == null || config.getWindow().isZero() || config.getWindow().isNegative()) {
                throw invalidIpControlConfiguration("ip-control window must be positive");
            }
            if (config.getMaxTtl() == null || config.getMaxTtl().isZero() || config.getMaxTtl().isNegative()) {
                throw invalidIpControlConfiguration("ip-control max-ttl must be positive");
            }
            if (config.getCapacity() <= 0) {
                throw invalidIpControlConfiguration("ip-control capacity must be positive");
            }
        }

        private static void requireNonEmptyText(List<String> values, String name) {
            if (values == null || values.isEmpty()) {
                throw invalidIpControlConfiguration("ip-control " + name + " must not be empty");
            }
            requireTextEntries(values, name);
        }

        private static void requireTextEntries(List<String> values, String name) {
            if (values == null) {
                throw invalidIpControlConfiguration("ip-control " + name + " must not be null");
            }
            for (String value : values) {
                if (value == null || value.trim().isEmpty()) {
                    throw invalidIpControlConfiguration(
                        "ip-control " + name + " must contain only non-blank values");
                }
            }
        }

        private static MonitoringConfigurationException invalidIpControlConfiguration(String message) {
            return new MonitoringConfigurationException(MonitoringErrorCode.INVALID_FIELD_VALUE, message);
        }
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
                                                                   ActionEventRecorder recorder) {
        return new ResourceAccessGuard(authorizer, recorder, Clock.systemUTC());
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
                                                                  MonitoringActionRegistry actions,
                                                                  ObjectProvider<EventEnricher> enrichers) {
        List<EventEnricher> values = new ArrayList<EventEnricher>();
        for (EventEnricher enricher : enrichers) {
            values.add(enricher);
        }
        return new ActionEventRecorder(monitor, Clock.systemUTC(), actions, values);
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

        @Bean("abnormalAccessTypedMonitorActionAspect")
        @ConditionalOnMissingBean(TypedMonitorActionAspect.class)
        TypedMonitorActionAspect abnormalAccessTypedMonitorActionAspect(MonitoringService monitoring,
                MonitoringContextAccessor context) {
            return new TypedMonitorActionAspect(monitoring, context);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.HandlerInterceptor")
    static class ServletMvcConfiguration {
        @Bean
        @ConditionalOnMissingBean(MonitoringContextAccessor.class)
        ServletMonitoringContextAccessor abnormalAccessMonitoringContextAccessor() {
            return new ServletMonitoringContextAccessor();
        }

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
