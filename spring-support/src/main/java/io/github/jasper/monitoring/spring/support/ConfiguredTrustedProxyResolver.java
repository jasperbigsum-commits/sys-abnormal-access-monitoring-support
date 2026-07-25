package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.TrustedProxyResolver;
import io.github.jasper.monitoring.spring.support.control.IpAddressLiteral;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 仅在直连对端被显式信任时解析转发客户端地址。
 *
 * <p>这可阻止未受信任客户端直接伪造 {@code X-Forwarded-For}。可信地址可以是 IP 字面量或 CIDR
 * 网段；配置格式错误的条目会被忽略，绝不会被当作可信代理。</p>
 */
public final class ConfiguredTrustedProxyResolver implements TrustedProxyResolver {
    private final List<TrustedAddress> trustedAddresses;

    /**
     * @param trustedProxies 可信反向代理的 IP 字面量或 CIDR 网段；格式错误的条目会被忽略
     */
    public ConfiguredTrustedProxyResolver(List<String> trustedProxies) {
        List<TrustedAddress> values = new ArrayList<TrustedAddress>();
        if (trustedProxies != null) {
            for (String trustedProxy : trustedProxies) {
                TrustedAddress address = TrustedAddress.parse(trustedProxy);
                if (address != null) {
                    values.add(address);
                }
            }
        }
        trustedAddresses = Collections.unmodifiableList(values);
    }

    /**
     * 仅当直连对端命中可信集合时，才从转发头选择第一个语法合法的客户端地址。
     *
     * @param directRemoteAddress 服务器提供的直连套接字对端地址
     * @param forwardedForHeader 可选的转发客户端地址请求头
     * @return 可信代理转发的客户端地址；否则返回直连对端地址
     */
    @Override
    public String resolveClientIp(String directRemoteAddress, String forwardedForHeader) {
        String directAddress = IpAddressLiteral.canonicalize(directRemoteAddress);
        if (!isTrusted(directRemoteAddress) || forwardedForHeader == null || forwardedForHeader.trim().isEmpty()) {
            return directAddress;
        }
        String[] values = forwardedForHeader.split(",");
        for (String value : values) {
            String candidate = value.trim();
            String canonical = IpAddressLiteral.canonicalize(candidate);
            if (canonical != null) {
                return canonical;
            }
        }
        return directAddress;
    }

    private boolean isTrusted(String address) {
        byte[] candidate = IpAddressLiteral.parseBytes(address);
        if (candidate == null) {
            return false;
        }
        for (TrustedAddress trustedAddress : trustedAddresses) {
            if (trustedAddress.matches(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static final class TrustedAddress {
        private final byte[] address;
        private final int prefixLength;

        private TrustedAddress(byte[] address, int prefixLength) {
            this.address = address;
            this.prefixLength = prefixLength;
        }

        private static TrustedAddress parse(String value) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            String[] parts = value.trim().split("/", -1);
            byte[] address = IpAddressLiteral.parseBytes(parts[0]);
            if (address == null || parts.length > 2) {
                return null;
            }
            int prefixLength = address.length * 8;
            if (parts.length == 2) {
                try {
                    prefixLength = Integer.parseInt(parts[1]);
                } catch (NumberFormatException exception) {
                    return null;
                }
            }
            return prefixLength >= 0 && prefixLength <= address.length * 8
                ? new TrustedAddress(address, prefixLength) : null;
        }

        private boolean matches(byte[] candidate) {
            if (candidate.length != address.length) {
                return false;
            }
            int wholeBytes = prefixLength / 8;
            for (int index = 0; index < wholeBytes; index++) {
                if (address[index] != candidate[index]) {
                    return false;
                }
            }
            int remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (address[wholeBytes] & mask) == (candidate[wholeBytes] & mask);
        }
    }
}
