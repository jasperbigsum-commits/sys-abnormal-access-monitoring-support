package io.github.jasper.monitoring.api;

import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 由框架适配器采集且已清洗的、不依赖具体请求容器的请求事实。
 *
 * <p>仅应包含可信请求头；身份、授权和源地址决策始终由服务端负责。</p>
 */
public final class MonitoringRequestContext {
    private final String method;
    private final String path;
    private final String sourceIp;
    private final String requestId;
    private final String traceId;
    private final Map<String, String> trustedHeaders;
    private MonitoringRequestContext(Builder builder) {
        method = required(builder.method, "method", 16);
        path = required(builder.path, "path", 512);
        sourceIp = required(builder.sourceIp, "sourceIp", 128);
        requestId = required(builder.requestId, "requestId", 128);
        traceId = SecurityFieldSanitizer.text(builder.traceId, 128);
        trustedHeaders = Collections.unmodifiableMap(new LinkedHashMap<String, String>(builder.trustedHeaders));
    }
    /** @return 用于创建不可变请求上下文快照的构建器（Builder） */
    public static Builder builder() { return new Builder(); }

    /** @return 已规范化的请求方法（HTTP method） */
    public String getMethod() { return method; }

    /** @return 已规范化的请求路径 */
    public String getPath() { return path; }

    /** @return 由适配器解析的可信客户端网络地址（IP） */
    public String getSourceIp() { return sourceIp; }

    /** @return 请求关联标识 */
    public String getRequestId() { return requestId; }

    /** @return 分布式追踪标识；不可用时为 {@code null} */
    public String getTraceId() { return traceId; }

    /** @return 用于关联的、宿主系统批准的不可变请求头集合 */
    public Map<String, String> getTrustedHeaders() { return trustedHeaders; }
    private static String required(String value, String field, int length) {
        String sanitized = SecurityFieldSanitizer.text(value, length);
        if (sanitized == null || sanitized.isEmpty()) {
            throw new MonitoringValidationException(MonitoringErrorCode.REQUIRED_FIELD_MISSING,
                field + " is required");
        }
        return sanitized;
    }
    /** {@link MonitoringRequestContext} 的构建器（Builder）。 */
    public static final class Builder {
        private String method;
        private String path;
        private String sourceIp;
        private String requestId;
        private String traceId;
        private final Map<String, String> trustedHeaders = new LinkedHashMap<String, String>();
        /**
         * 设置请求方法。
         *
         * @param value 请求方法（HTTP method）
         * @return 当前构建器
         */
        public Builder method(String value) { method = value; return this; }
        /**
         * 设置请求路径。
         *
         * @param value 请求路径
         * @return 当前构建器
         */
        public Builder path(String value) { path = value; return this; }
        /**
         * 设置可信客户端网络地址。
         *
         * @param value 可信客户端网络地址（IP）
         * @return 当前构建器
         */
        public Builder sourceIp(String value) { sourceIp = value; return this; }
        /**
         * 设置请求关联标识。
         *
         * @param value 请求关联标识
         * @return 当前构建器
         */
        public Builder requestId(String value) { requestId = value; return this; }
        /**
         * 设置分布式追踪标识。
         *
         * @param value 分布式追踪标识，可用时提供
         * @return 当前构建器
         */
        public Builder traceId(String value) { traceId = value; return this; }

        /**
         * 添加宿主系统批准的关联请求头。
         *
         * @param key 非敏感请求头名称
         * @param value 已清洗的请求头值
         * @return 当前构建器
         */
        public Builder trustedHeader(String key, String value) {
            SecurityFieldSanitizer.requireSafeAttributeKey(key);
            trustedHeaders.put(SecurityFieldSanitizer.text(key, 128), SecurityFieldSanitizer.text(value, 512));
            return this;
        }
        /**
         * 校验必需请求事实后构建不可变上下文。
         *
         * @return 不可变请求上下文
         * @throws IllegalArgumentException 当缺少必需事实或存在不安全字段时抛出
         */
        public MonitoringRequestContext build() { return new MonitoringRequestContext(this); }
    }
}
