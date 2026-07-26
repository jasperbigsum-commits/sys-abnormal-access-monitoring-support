package io.github.jasper.monitoring.api.management.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ManagementModelValidation {
    private ManagementModelValidation() {
    }

    static String text(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    static String status(String value, String field) {
        String normalized = text(value, field);
        for (int i = 0; i < normalized.length(); i++) {
            char character = normalized.charAt(i);
            if (!(character >= 'A' && character <= 'Z') && character != '_') {
                throw new IllegalArgumentException(field + " must be an uppercase status token");
            }
        }
        return normalized;
    }

    static String code(String value, String field) {
        String normalized = text(value, field);
        for (int i = 0; i < normalized.length(); i++) {
            char character = normalized.charAt(i);
            if (!((character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == ':' || character == '_' || character == '-' || character == '.')) {
                throw new IllegalArgumentException(field + " must be a code token");
            }
        }
        return normalized;
    }

    static String alertStatus(String value) {
        String normalized = status(value, "status");
        try {
            AlertStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("status is not a valid alert status", exception);
        }
        return normalized;
    }

    static String controlStatus(String value) {
        String normalized = status(value, "status");
        try {
            io.github.jasper.monitoring.api.control.ControlStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            if (!"UNKNOWN".equals(normalized)) {
                throw new IllegalArgumentException("status is not a valid control status", exception);
            }
        }
        return normalized;
    }

    static long positive(long value, String field) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    static long timestamp(long value, String field) {
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be a positive epoch timestamp");
        }
        return value;
    }

    static List<ControlAttemptView> attempts(List<ControlAttemptView> values) {
        if (values == null) {
            throw new IllegalArgumentException("attempts must not be null");
        }
        List<ControlAttemptView> copy = new ArrayList<ControlAttemptView>(values.size());
        long previous = 0;
        for (ControlAttemptView value : values) {
            if (value == null) {
                throw new IllegalArgumentException("attempts must not contain null");
            }
            if (value.getAttempt() <= previous) {
                throw new IllegalArgumentException("attempts must be strictly increasing");
            }
            previous = value.getAttempt();
            copy.add(value);
        }
        return Collections.unmodifiableList(copy);
    }
}
