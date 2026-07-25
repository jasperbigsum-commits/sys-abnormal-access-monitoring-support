package io.github.jasper.monitoring.spring.support.control;

/** Parses and canonicalizes IPv4 and IPv6 literals without name service lookups. */
public final class IpAddressLiteral {
    private static final int MAX_LITERAL_LENGTH = 45;

    private IpAddressLiteral() {
    }

    public static String canonicalize(String value) {
        if (value == null) {
            return null;
        }
        String literal = value.trim();
        if (literal.isEmpty() || literal.length() > MAX_LITERAL_LENGTH) {
            return null;
        }
        if (literal.indexOf(':') < 0) {
            return parseIpv4(literal) == null ? null : literal;
        }
        int[] groups = parseIpv6(literal);
        return groups == null ? null : formatIpv6(groups);
    }

    public static byte[] parseBytes(String value) {
        String canonical = canonicalize(value);
        if (canonical == null) {
            return null;
        }
        if (canonical.indexOf(':') < 0) {
            int[] octets = parseIpv4(canonical);
            byte[] bytes = new byte[4];
            for (int index = 0; index < octets.length; index++) {
                bytes[index] = (byte) octets[index];
            }
            return bytes;
        }
        int[] groups = parseIpv6(canonical);
        byte[] bytes = new byte[16];
        for (int index = 0; index < groups.length; index++) {
            bytes[index * 2] = (byte) (groups[index] >>> 8);
            bytes[index * 2 + 1] = (byte) groups[index];
        }
        return bytes;
    }

    private static int[] parseIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return null;
        }
        int[] parsed = new int[4];
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];
            if (octet.isEmpty() || octet.length() > 3
                || octet.length() > 1 && octet.charAt(0) == '0') {
                return null;
            }
            int number = 0;
            for (int characterIndex = 0; characterIndex < octet.length(); characterIndex++) {
                char character = octet.charAt(characterIndex);
                if (character < '0' || character > '9') {
                    return null;
                }
                number = number * 10 + character - '0';
            }
            if (number > 255) {
                return null;
            }
            parsed[index] = number;
        }
        return parsed;
    }

    private static int[] parseIpv6(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!(character == ':' || character == '.' || character >= '0' && character <= '9'
                || character >= 'a' && character <= 'f'
                || character >= 'A' && character <= 'F')) {
                return null;
            }
        }
        int compression = value.indexOf("::");
        if (compression >= 0 && value.indexOf("::", compression + 2) >= 0) {
            return null;
        }
        String left = compression < 0 ? value : value.substring(0, compression);
        String right = compression < 0 ? "" : value.substring(compression + 2);
        String[] leftGroups = left.isEmpty() ? new String[0] : left.split(":", -1);
        String[] rightGroups = right.isEmpty() ? new String[0] : right.split(":", -1);
        if (!hasValidEmbeddedIpv4Position(leftGroups, rightGroups, compression >= 0)) {
            return null;
        }
        int leftCount = groupCount(leftGroups);
        int rightCount = groupCount(rightGroups);
        if (leftCount < 0 || rightCount < 0) {
            return null;
        }
        int explicit = leftCount + rightCount;
        if (compression < 0 ? explicit != 8 : explicit >= 8) {
            return null;
        }
        int[] groups = new int[8];
        int index = appendGroups(groups, 0, leftGroups);
        if (index < 0 || appendGroups(groups, groups.length - rightCount, rightGroups) < 0) {
            return null;
        }
        return groups;
    }

    private static boolean hasValidEmbeddedIpv4Position(String[] leftGroups, String[] rightGroups,
                                                        boolean compressed) {
        int dottedCount = 0;
        for (String group : leftGroups) {
            dottedCount += group.indexOf('.') >= 0 ? 1 : 0;
        }
        for (String group : rightGroups) {
            dottedCount += group.indexOf('.') >= 0 ? 1 : 0;
        }
        if (dottedCount == 0) {
            return true;
        }
        if (compressed && rightGroups.length == 0) {
            return false;
        }
        String last = rightGroups.length > 0 ? rightGroups[rightGroups.length - 1]
            : leftGroups.length > 0 ? leftGroups[leftGroups.length - 1] : "";
        return dottedCount == 1 && last.indexOf('.') >= 0;
    }

    private static int groupCount(String[] values) {
        int count = 0;
        for (String value : values) {
            if (value.indexOf('.') >= 0) {
                if (parseIpv4(value) == null) {
                    return -1;
                }
                count += 2;
            } else {
                count++;
            }
        }
        return count;
    }

    private static int appendGroups(int[] target, int index, String[] values) {
        for (String value : values) {
            if (value.indexOf('.') >= 0) {
                int[] octets = parseIpv4(value);
                if (octets == null || index + 1 >= target.length) {
                    return -1;
                }
                target[index++] = octets[0] << 8 | octets[1];
                target[index++] = octets[2] << 8 | octets[3];
            } else {
                Integer parsed = parseGroup(value);
                if (parsed == null || index >= target.length) {
                    return -1;
                }
                target[index++] = parsed;
            }
        }
        return index;
    }

    private static Integer parseGroup(String group) {
        if (group.isEmpty() || group.length() > 4) {
            return null;
        }
        try {
            return Integer.valueOf(group, 16);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private static String formatIpv6(int[] groups) {
        int bestStart = -1;
        int bestLength = 0;
        for (int start = 0; start < groups.length; start++) {
            if (groups[start] != 0) {
                continue;
            }
            int end = start;
            while (end < groups.length && groups[end] == 0) {
                end++;
            }
            if (end - start > bestLength && end - start >= 2) {
                bestStart = start;
                bestLength = end - start;
            }
            start = end - 1;
        }
        if (bestStart < 0) {
            return join(groups, 0, groups.length);
        }
        return join(groups, 0, bestStart) + "::" + join(groups, bestStart + bestLength, groups.length);
    }

    private static String join(int[] groups, int start, int end) {
        StringBuilder value = new StringBuilder();
        for (int index = start; index < end; index++) {
            if (value.length() > 0) {
                value.append(':');
            }
            value.append(Integer.toHexString(groups[index]));
        }
        return value.toString();
    }
}
