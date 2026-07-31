package io.github.jasper.monitoring.audit.spring3.management;

import java.util.Collections;
import java.util.Map;

/** Jeecg-compatible management HTTP response envelope. */
public final class ManagementResult<T> {
    private final boolean success;
    private final int code;
    private final String message;
    private final T result;
    private final long timestamp;

    private ManagementResult(boolean success, int code, String message, T result) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.result = result;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> ManagementResult<T> ok(T result) {
        return new ManagementResult<T>(true, 200, "操作成功", result);
    }

    public static ManagementResult<Map<String, Object>> failure(int code, String message, String errorType) {
        return new ManagementResult<Map<String, Object>>(false, code, message,
            Collections.<String, Object>singletonMap("errorType", errorType));
    }

    public boolean isSuccess() { return success; }
    public int getCode() { return code; }
    public String getMessage() { return message; }
    public T getResult() { return result; }
    public long getTimestamp() { return timestamp; }
}
