package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.api.IdentityContext;
import io.github.jasper.monitoring.api.IdentityContextProvider;
import io.github.jasper.monitoring.api.MonitorAction;
import io.github.jasper.monitoring.api.MonitorActionDefinition;
import io.github.jasper.monitoring.api.MonitorActionFacts;
import io.github.jasper.monitoring.api.MonitoringRequestContext;
import io.github.jasper.monitoring.api.SecurityEventDraft;
import io.github.jasper.monitoring.api.SecurityEventResult;
import io.github.jasper.monitoring.api.TrustedProxyResolver;
import io.github.jasper.monitoring.core.application.ActionEventRecorder;
import io.github.jasper.monitoring.spring.support.MdcTraceBridge;
import io.github.jasper.monitoring.spring.support.AnnotatedActionFacts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpEntity;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * 记录带 {@link MonitorAction} 的已完成 MVC 动作，不改变宿主控制器的执行路径。
 *
 * <p>方法注解覆盖类型注解；仅 {@code HandlerMethod} 会被自动处理。该拦截器在请求完成后根据服务端状态
 * 写入事件，监测失败不会改变已完成响应。它不等价于 Service AOP，非 MVC 入口应使用注册式方法调用埋点。</p>
 */
public final class AnnotatedActionMonitoringInterceptor implements HandlerInterceptor {
    private static final ReactiveAdapterRegistry REACTIVE_ADAPTERS = ReactiveAdapterRegistry.getSharedInstance();
    private final ActionEventRecorder recorder;
    private final TrustedProxyResolver trustedProxyResolver;
    private final IdentityContextProvider identityContextProvider;
    private final MdcTraceBridge mdcTraceBridge;

    /**
     * @param recorder 注解与方法调用共用的标准记录器
     * @param trustedProxyResolver 宿主认可的客户端地址解析器
     * @param identityContextProvider 服务端身份解析器
     */
    public AnnotatedActionMonitoringInterceptor(ActionEventRecorder recorder,
                                                TrustedProxyResolver trustedProxyResolver,
                                                IdentityContextProvider identityContextProvider) {
        this(recorder, trustedProxyResolver, identityContextProvider, MdcTraceBridge.create(false, "traceId"));
    }

    /**
     * @param recorder 注解与方法调用共用的标准记录器
     * @param trustedProxyResolver 宿主认可的客户端地址解析器
     * @param identityContextProvider 服务端身份解析器
     * @param mdcTraceBridge 可选日志 MDC 链路追踪桥接器
     */
    public AnnotatedActionMonitoringInterceptor(ActionEventRecorder recorder,
                                                TrustedProxyResolver trustedProxyResolver,
                                                IdentityContextProvider identityContextProvider,
                                                MdcTraceBridge mdcTraceBridge) {
        this.recorder = recorder;
        this.trustedProxyResolver = trustedProxyResolver;
        this.identityContextProvider = identityContextProvider;
        this.mdcTraceBridge = mdcTraceBridge;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            AnnotatedActionFacts facts = facts(handler);
            if (facts == null) {
                return true;
            }
            request.setAttribute(AnnotatedActionFacts.REQUEST_ATTRIBUTE, facts);
            RequestMetadataInterceptor.populate(request, trustedProxyResolver, identityContextProvider,
                mdcTraceBridge, "annotated-action");
        } catch (RuntimeException ignored) {
            // Monitoring is observational and must not block the host action.
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        try {
            Object action = request.getAttribute(AnnotatedActionFacts.REQUEST_ATTRIBUTE);
            Object requestValue = request.getAttribute(RequestMetadataInterceptor.REQUEST_CONTEXT_ATTRIBUTE);
            Object identityValue = request.getAttribute(RequestMetadataInterceptor.IDENTITY_CONTEXT_ATTRIBUTE);
            if (!(action instanceof AnnotatedActionFacts) || !(requestValue instanceof MonitoringRequestContext)
                || !(identityValue instanceof IdentityContext)) {
                return;
            }
            AnnotatedActionFacts facts = (AnnotatedActionFacts) action;
            MonitorActionFacts dynamic = facts.snapshot();
            SecurityEventDraft.Builder draft = recorder.draft(facts.getDefinition(),
                (MonitoringRequestContext) requestValue, (IdentityContext) identityValue);
            facts.apply(draft, dynamic);
            recorder.record(draft.result(result(response, exception, dynamic))
                    .reasonCode(reasonCode(response, exception, dynamic)).build(), facts.getInputValidation(),
                (MonitoringRequestContext) requestValue, (IdentityContext) identityValue);
        } catch (RuntimeException ignored) {
            // Monitoring is observational and must not change the completed MVC response.
        } finally {
            RequestMetadataInterceptor.clearMdcScope(request, "annotated-action");
        }
    }

    private static AnnotatedActionFacts facts(Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return null;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        AnnotatedActionSourceResolver.ResolvedAction action = AnnotatedActionSourceResolver.resolve(
            handlerMethod.getMethod(), targetType(handlerMethod));
        if (action == null || isAsyncOrStreaming(action.getMethod())) {
            return null;
        }
        return new AnnotatedActionFacts(MonitorActionDefinition.from(action.getSource()), action.getMethod(),
            Arrays.asList(action.getAction().enrichers()));
    }

    private static Class<?> targetType(HandlerMethod handlerMethod) {
        Object bean = handlerMethod.getBean();
        if (bean != null && !(bean instanceof String)) {
            Class<?> targetType = AopProxyUtils.ultimateTargetClass(bean);
            if (targetType != null) {
                return targetType;
            }
        }
        return handlerMethod.getBeanType();
    }

    private static boolean isAsyncOrStreaming(Method method) {
        return isAsyncOrStreaming(method.getGenericReturnType(), new HashSet<Type>());
    }

    private static boolean isAsyncOrStreaming(Type returnType, Set<Type> inspectedTypes) {
        if (returnType == null || !inspectedTypes.add(returnType)) {
            return false;
        }
        if (returnType instanceof Class<?>) {
            Class<?> returnClass = (Class<?>) returnType;
            if (returnClass.isArray()) {
                return isAsyncOrStreaming(returnClass.getComponentType(), inspectedTypes);
            }
            return isAsyncOrStreamingClass(returnClass)
                || hasAsyncOrStreamingHttpEntityPayload(ResolvableType.forClass(returnClass), inspectedTypes);
        }
        if (returnType instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) returnType;
            Type rawType = parameterized.getRawType();
            if (rawType instanceof Class<?> && isAsyncOrStreamingClass((Class<?>) rawType)) {
                return true;
            }
            return hasAsyncOrStreamingHttpEntityPayload(ResolvableType.forType(parameterized), inspectedTypes);
        }
        if (returnType instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) returnType;
            Type[] upperBounds = wildcard.getUpperBounds();
            Type[] lowerBounds = wildcard.getLowerBounds();
            for (Type upperBound : upperBounds) {
                if (isAsyncOrStreaming(upperBound, inspectedTypes)) {
                    return true;
                }
            }
            for (Type lowerBound : lowerBounds) {
                if (isAsyncOrStreaming(lowerBound, inspectedTypes)) {
                    return true;
                }
            }
            return hasUnresolvedWildcardBounds(upperBounds, lowerBounds);
        }
        if (returnType instanceof TypeVariable<?>) {
            TypeVariable<?> variable = (TypeVariable<?>) returnType;
            Type[] bounds = variable.getBounds();
            for (Type bound : bounds) {
                if (isAsyncOrStreaming(bound, inspectedTypes)) {
                    return true;
                }
            }
            return !hasKnownSynchronousBound(bounds);
        }
        if (returnType instanceof GenericArrayType) {
            return isAsyncOrStreaming(((GenericArrayType) returnType).getGenericComponentType(), inspectedTypes);
        }
        return false;
    }

    private static boolean isUnresolvedHttpEntityPayload(Type payloadType) {
        if (payloadType == Object.class) {
            return true;
        }
        if (payloadType instanceof TypeVariable<?>) {
            return !hasKnownSynchronousBound(((TypeVariable<?>) payloadType).getBounds());
        }
        if (payloadType instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) payloadType;
            return hasUnresolvedWildcardBounds(wildcard.getUpperBounds(), wildcard.getLowerBounds());
        }
        return false;
    }

    private static boolean hasUnresolvedWildcardBounds(Type[] upperBounds, Type[] lowerBounds) {
        return lowerBounds.length > 0 || !hasKnownSynchronousBound(upperBounds);
    }

    private static boolean hasKnownSynchronousBound(Type[] bounds) {
        for (Type bound : bounds) {
            if (bound instanceof Class<?>) {
                Class<?> boundClass = (Class<?>) bound;
                if (Modifier.isFinal(boundClass.getModifiers()) && !isAsyncOrStreamingClass(boundClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAsyncOrStreamingHttpEntityPayload(ResolvableType returnType,
                                                                  Set<Type> inspectedTypes) {
        ResolvableType httpEntity = withKnownSynchronousGenericBounds(returnType).as(HttpEntity.class);
        if (httpEntity == ResolvableType.NONE) {
            return false;
        }
        if (httpEntity.hasUnresolvableGenerics()) {
            return true;
        }
        for (ResolvableType payload : httpEntity.getGenerics()) {
            Type payloadType = payload.getType();
            Class<?> resolvedPayload = payload.resolve();
            if (resolvedPayload != null) {
                if (isAsyncOrStreaming(resolvedPayload, inspectedTypes)) {
                    return true;
                }
                if (resolvedPayload == Object.class) {
                    return true;
                }
                continue;
            }
            if (isAsyncOrStreaming(payloadType, inspectedTypes) || isUnresolvedHttpEntityPayload(payloadType)) {
                return true;
            }
        }
        return false;
    }

    private static ResolvableType withKnownSynchronousGenericBounds(ResolvableType returnType) {
        if (!(returnType.getType() instanceof ParameterizedType)) {
            return returnType;
        }
        Class<?> returnClass = returnType.resolve();
        if (returnClass == null) {
            return returnType;
        }
        TypeVariable<?>[] parameters = returnClass.getTypeParameters();
        ResolvableType[] generics = returnType.getGenerics();
        if (parameters.length == 0 || parameters.length != generics.length) {
            return returnType;
        }
        ResolvableType[] resolvedGenerics = new ResolvableType[generics.length];
        boolean resolvedBound = false;
        for (int i = 0; i < generics.length; i++) {
            Type genericType = generics[i].getType();
            if ((genericType instanceof TypeVariable<?> || genericType instanceof WildcardType)
                && hasKnownSynchronousBound(parameters[i].getBounds())) {
                resolvedGenerics[i] = ResolvableType.forType(parameters[i].getBounds()[0]);
                resolvedBound = true;
            } else {
                resolvedGenerics[i] = generics[i];
            }
        }
        return resolvedBound ? ResolvableType.forClassWithGenerics(returnClass, resolvedGenerics) : returnType;
    }

    private static boolean isAsyncOrStreamingClass(Class<?> returnType) {
        return Callable.class.isAssignableFrom(returnType)
            || Future.class.isAssignableFrom(returnType)
            || CompletionStage.class.isAssignableFrom(returnType)
            || DeferredResult.class.isAssignableFrom(returnType)
            || WebAsyncTask.class.isAssignableFrom(returnType)
            || ResponseBodyEmitter.class.isAssignableFrom(returnType)
            || StreamingResponseBody.class.isAssignableFrom(returnType)
            || REACTIVE_ADAPTERS.getAdapter(returnType) != null;
    }

    private static SecurityEventResult result(HttpServletResponse response, Exception exception,
                                              MonitorActionFacts facts) {
        if (exception != null) {
            return SecurityEventResult.FAILURE;
        }
        int status = response.getStatus();
        if (status == HttpServletResponse.SC_UNAUTHORIZED || status == HttpServletResponse.SC_FORBIDDEN) {
            return SecurityEventResult.DENIED;
        }
        if (status >= HttpServletResponse.SC_BAD_REQUEST) {
            return SecurityEventResult.FAILURE;
        }
        return facts.getResult() == null ? SecurityEventResult.SUCCESS : facts.getResult();
    }

    private static String reasonCode(HttpServletResponse response, Exception exception, MonitorActionFacts facts) {
        if (exception != null) {
            return "HANDLER_EXCEPTION";
        }
        int status = response.getStatus();
        if (status == HttpServletResponse.SC_UNAUTHORIZED || status == HttpServletResponse.SC_FORBIDDEN) {
            return "HTTP_" + status;
        }
        return status >= HttpServletResponse.SC_BAD_REQUEST ? "HTTP_" + status : facts.getReasonCode();
    }
}
