package io.github.jasper.monitoring.spring.support;

import io.github.jasper.monitoring.api.TrustedProxyResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves forwarded client addresses only when the direct peer is explicitly trusted.
 * This prevents an untrusted client from spoofing {@code X-Forwarded-For} directly.
 */
public final class ConfiguredTrustedProxyResolver implements TrustedProxyResolver {
    private final List<TrustedAddress> trustedAddresses;

    /**
     * @param trustedProxies literal IP addresses or CIDR ranges for trusted reverse proxies;
     *                       malformed entries are ignored rather than trusted
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
     * Selects the first syntactically valid client address from the forwarding header only when the direct peer
     * matches the configured trusted set.
     *
     * @param directRemoteAddress direct socket peer address supplied by the server
     * @param forwardedForHeader optional forwarded-client header
     * @return a forwarded client address for a trusted proxy, otherwise the direct peer address
     */
    @Override
    public String resolveClientIp(String directRemoteAddress, String forwardedForHeader) {
        if (!isTrusted(directRemoteAddress) || forwardedForHeader == null || forwardedForHeader.trim().isEmpty()) {
            return directRemoteAddress;
        }
        String[] values = forwardedForHeader.split(",");
        for (String value : values) {
            String candidate = value.trim();
            if (!candidate.isEmpty() && parseAddress(candidate) != null) {
                return candidate;
            }
        }
        return directRemoteAddress;
    }

    private boolean isTrusted(String address) {
        byte[] candidate = parseAddress(address);
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

    private static byte[] parseAddress(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return InetAddress.getByName(value.trim()).getAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
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
            byte[] address = parseAddress(parts[0]);
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
