package com.shutdowntracker.api.importedproject;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

final class ImportedProjectRecordValidation {

    private static final BigDecimal MIN_PERCENT = BigDecimal.ZERO;
    private static final BigDecimal MAX_PERCENT = new BigDecimal("100");

    private ImportedProjectRecordValidation() {
    }

    static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    static <T> T requireNonNull(T value, String message) {
        return Objects.requireNonNull(value, message);
    }

    static void requireNonNegative(Integer value, String fieldName) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative.");
        }
    }

    static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative.");
        }
    }

    static void requirePercent(BigDecimal value, String fieldName) {
        if (value != null && (value.compareTo(MIN_PERCENT) < 0 || value.compareTo(MAX_PERCENT) > 0)) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 100.");
        }
    }

    static void requireOrderedDates(OffsetDateTime start, OffsetDateTime finish, String message) {
        if (start != null && finish != null && finish.isBefore(start)) {
            throw new IllegalArgumentException(message);
        }
    }

    static Map<String, Object> immutableObjectMap(Map<String, Object> value, String fieldName) {
        if (value == null) {
            return Map.of();
        }
        if (value.containsKey(null)) {
            throw new IllegalArgumentException(fieldName + " must not contain null keys.");
        }
        return Map.copyOf(value);
    }
}
