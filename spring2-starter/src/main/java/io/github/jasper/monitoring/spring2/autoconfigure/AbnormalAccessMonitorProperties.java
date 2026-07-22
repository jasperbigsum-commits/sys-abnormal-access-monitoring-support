package io.github.jasper.monitoring.spring2.autoconfigure;

import io.github.jasper.monitoring.api.MonitoringMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External configuration for the Spring Boot 2 starter.
 *
 * <p>Use the {@code abnormal.access.monitor} prefix. Defaults are deliberately safe: observe
 * mode, frontend collection enabled, and no trusted proxies.</p>
 */
@ConfigurationProperties("abnormal.access.monitor")
public class AbnormalAccessMonitorProperties {
    private String systemId = "application";
    private MonitoringMode mode = MonitoringMode.OBSERVE;
    private Frontend frontend = new Frontend();
    private List<String> trustedProxies = new ArrayList<String>();

    /** Returns the stable identifier written into every security event. */
    public String getSystemId() { return systemId; }
    /** Sets the stable identifier written into every security event. */
    public void setSystemId(String systemId) { this.systemId = systemId; }
    /** Returns the monitoring mode. */
    public MonitoringMode getMode() { return mode; }
    /** Sets the monitoring mode. */
    public void setMode(MonitoringMode mode) { this.mode = mode; }
    /** Returns frontend collection settings. */
    public Frontend getFrontend() { return frontend; }
    /** Replaces frontend collection settings; {@code null} restores defaults. */
    public void setFrontend(Frontend frontend) { this.frontend = frontend == null ? new Frontend() : frontend; }
    /** Returns trusted reverse-proxy addresses or CIDR ranges. */
    public List<String> getTrustedProxies() { return trustedProxies; }
    /** Replaces trusted reverse-proxy addresses or CIDR ranges. */
    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? new ArrayList<String>() : new ArrayList<String>(trustedProxies);
    }

    /** Frontend telemetry collection settings. */
    public static class Frontend {
        private boolean enabled = true;
        /** Returns whether request metadata collection is enabled. */
        public boolean isEnabled() { return enabled; }
        /** Enables or disables request metadata collection. */
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
