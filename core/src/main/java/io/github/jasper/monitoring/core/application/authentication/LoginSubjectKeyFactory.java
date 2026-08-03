package io.github.jasper.monitoring.core.application.authentication;

import io.github.jasper.monitoring.api.authentication.LoginSubjectCanonicalizer;
import io.github.jasper.monitoring.api.authentication.LoginSubjectInput;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 为认证控制和 Fact 派生稳定、不可逆的 opaque 登录主体。
 *
 * <p>输出格式为 {@code v1:<base64url-hmac>}，HMAC 输入包含认证域和规范账号值。
 * 同一系统的所有实例必须使用相同密钥；更换密钥会改变主体值，使既有规则窗口和未过期控制
 * 无法继续命中。该类不会保存或输出原始登录标识。</p>
 */
public final class LoginSubjectKeyFactory {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] secret;
    private final LoginSubjectCanonicalizer canonicalizer;

    /**
     * 创建登录主体密钥工厂。
     *
     * @param secret 至少 32 字节的 HMAC 密钥；构造器会保存防御性副本
     * @param canonicalizer 宿主账号别名规范化器
     * @throws NullPointerException 任一参数为空时抛出
     * @throws IllegalArgumentException 密钥不足 32 字节时抛出
     */
    public LoginSubjectKeyFactory(byte[] secret, LoginSubjectCanonicalizer canonicalizer) {
        Objects.requireNonNull(secret, "secret");
        if (secret.length < 32) {
            throw new IllegalArgumentException("authentication subject key secret must contain at least 32 bytes");
        }
        this.secret = secret.clone();
        this.canonicalizer = Objects.requireNonNull(canonicalizer, "canonicalizer");
    }

    /**
     * 为一次登录主体输入生成稳定的 opaque key。
     *
     * @param input 包含登录标识和认证域的临时输入
     * @return 带 {@code v1:} 版本前缀的 Base64URL HMAC 主体
     * @throws NullPointerException 输入或规范化结果为空时抛出
     * @throws IllegalArgumentException 规范化结果为空白、超长或包含控制字符时抛出
     * @throws IllegalStateException 当前运行环境无法提供 HMAC-SHA256 时抛出
     */
    public String generate(LoginSubjectInput input) {
        Objects.requireNonNull(input, "input");
        String canonical = Objects.requireNonNull(canonicalizer.canonicalize(input), "canonical subject");
        if (canonical.trim().isEmpty() || canonical.length() > 256) {
            throw new IllegalArgumentException("canonical subject is blank or too long");
        }
        for (int i = 0; i < canonical.length(); i++) {
            if (Character.isISOControl(canonical.charAt(i))) {
                throw new IllegalArgumentException("canonical subject contains control characters");
            }
        }
        String payload = input.getRealm() + "\u0000" + canonical;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return "v1:" + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC subject key generation unavailable", ex);
        }
    }
}
