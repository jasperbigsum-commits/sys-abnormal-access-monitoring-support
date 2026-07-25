package io.github.jasper.monitoring.api;

import io.github.jasper.monitoring.api.error.MonitoringErrorCode;
import io.github.jasper.monitoring.api.error.MonitoringValidationException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 在字段成为可检索的安全证据前进行清洗。
 *
 * <p>本工具会移除控制字符、限制字段长度，并拒绝疑似携带凭据的属性键。其策略刻意保守：
 * 集成方只能传入标识和运行元数据，绝不能传入请求体或密钥。</p>
 */
public final class SecurityFieldSanitizer {
    private static final Set<String> FORBIDDEN_KEYWORDS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
        "password", "passwd", "token", "cookie", "secret", "credential", "authorization", "private_key", "apikey", "api_key", "sms_code")));

    private SecurityFieldSanitizer() {
    }

    /**
     * 规范化空白字符和控制字符，然后截断文本。
     *
     * @param value 原始文本，可为 {@code null}
     * @param maximumLength 允许保留的最大字符数
     * @return 清洗后的文本；当 {@code value} 为 {@code null} 时返回 {@code null}
     */
    public static String text(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        StringBuilder sanitized = new StringBuilder(value.length());
        boolean previousWhitespace = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)) {
                character = ' ';
            }
            if (Character.isWhitespace(character)) {
                if (!previousWhitespace) {
                    sanitized.append(' ');
                    previousWhitespace = true;
                }
            } else {
                sanitized.append(character);
                previousWhitespace = false;
            }
            if (sanitized.length() >= maximumLength) {
                break;
            }
        }
        return sanitized.toString().trim();
    }

    /**
     * 校验属性键非空，且不指向凭据类信息。
     *
     * @param key 待校验的属性键
     * @throws IllegalArgumentException 当属性键为空或包含禁止关键字时抛出
     */
    public static void requireSafeAttributeKey(String key) {
        String normalized = text(key, 128);
        if (normalized == null || normalized.isEmpty()) {
            throw new MonitoringValidationException(MonitoringErrorCode.REQUIRED_FIELD_MISSING,
                "Event attribute key is required");
        }
        String comparable = lettersAndDigits(normalized);
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (comparable.contains(lettersAndDigits(keyword))) {
                throw new MonitoringValidationException(MonitoringErrorCode.UNSAFE_EVENT_ATTRIBUTE,
                    "Event attributes must not contain credential material");
            }
        }
    }

    /**
     * 清洗、校验并规范化属性键。
     *
     * <p>所有事件和动作元数据使用同一规范化形式，避免大小写变体绕过静态属性和保留前缀保护。</p>
     *
     * @param key 待规范化的属性键
     * @return 已清洗的小写属性键
     * @throws IllegalArgumentException 当属性键为空或指向凭据类信息时抛出
     */
    public static String normalizeAttributeKey(String key) {
        String normalized = text(key, 128);
        requireSafeAttributeKey(normalized);
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String lettersAndDigits(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                normalized.append(Character.toLowerCase(character));
            }
        }
        return normalized.toString();
    }
}
