package io.github.jasper.monitoring.spring3.autoconfigure;

import io.github.jasper.monitoring.api.MonitoringMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring Boot 3 Starter 的外部配置。
 *
 * <p>使用 {@code abnormal.access.monitor} 前缀。默认值保持安全：观察模式、启用请求元数据采集、
 * 不信任任何反向代理，并在可用时把事件追踪标识同步到日志 MDC。</p>
 */
@ConfigurationProperties("abnormal.access.monitor")
public class AbnormalAccessMonitorProperties {
    private String systemId = "application";
    private MonitoringMode mode = MonitoringMode.OBSERVE;
    private Frontend frontend = new Frontend();
    private Instrumentation instrumentation = new Instrumentation();
    private Mdc mdc = new Mdc();
    private List<String> trustedProxies = new ArrayList<String>();

    /** @return 写入每个安全事件的稳定系统标识 */
    public String getSystemId() { return systemId; }
    /** @param systemId 写入每个安全事件的稳定系统标识 */
    public void setSystemId(String systemId) { this.systemId = systemId; }
    /** @return 当前监测运行模式 */
    public MonitoringMode getMode() { return mode; }
    /** @param mode 当前监测运行模式 */
    public void setMode(MonitoringMode mode) { this.mode = mode; }
    /** @return 前端补充证据与请求元数据采集配置 */
    public Frontend getFrontend() { return frontend; }
    /** @param frontend 前端补充证据与请求元数据采集配置；{@code null} 时恢复默认值 */
    public void setFrontend(Frontend frontend) { this.frontend = frontend == null ? new Frontend() : frontend; }
    /** @return Servlet MVC 注解动作采集配置 */
    public Instrumentation getInstrumentation() { return instrumentation; }
    /** @param instrumentation Servlet MVC 注解动作采集配置；{@code null} 时恢复默认值 */
    public void setInstrumentation(Instrumentation instrumentation) {
        this.instrumentation = instrumentation == null ? new Instrumentation() : instrumentation;
    }
    /** @return 受信任反向代理地址或 CIDR 范围 */
    public List<String> getTrustedProxies() { return trustedProxies; }
    /** @param trustedProxies 受信任反向代理地址或 CIDR 范围 */
    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? new ArrayList<String>() : new ArrayList<String>(trustedProxies);
    }

    /** @return 可选日志 MDC 链路追踪配置 */
    public Mdc getMdc() { return mdc; }

    /** @param mdc 可选日志 MDC 链路追踪配置；{@code null} 时恢复默认值 */
    public void setMdc(Mdc mdc) { this.mdc = mdc == null ? new Mdc() : mdc; }

    /** 前端补充证据与请求元数据采集配置。 */
    public static class Frontend {
        private boolean enabled = true;
        /** @return 是否启用请求元数据采集 */
        public boolean isEnabled() { return enabled; }
        /** @param enabled 是否启用请求元数据采集 */
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /** Servlet MVC 标注动作采集配置，不覆盖 Service、任务或消息消费。 */
    public static class Instrumentation {
        private boolean enabled = true;
        /** @return 是否自动记录带 {@code MonitorAction} 的 MVC 动作 */
        public boolean isEnabled() { return enabled; }
        /** @param enabled 是否自动记录带 {@code MonitorAction} 的 MVC 动作 */
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /** 可选日志 MDC 链路追踪配置。 */
    public static class Mdc {
        private boolean enabled = true;
        private String traceIdKey = "traceId";

        /** @return 是否尝试把事件追踪标识绑定到日志 MDC */
        public boolean isEnabled() { return enabled; }

        /** @param enabled 是否尝试把事件追踪标识绑定到日志 MDC */
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        /** @return 宿主日志格式中读取追踪标识的 MDC 键 */
        public String getTraceIdKey() { return traceIdKey; }

        /** @param traceIdKey 宿主日志格式中读取追踪标识的 MDC 键 */
        public void setTraceIdKey(String traceIdKey) { this.traceIdKey = traceIdKey; }
    }
}
