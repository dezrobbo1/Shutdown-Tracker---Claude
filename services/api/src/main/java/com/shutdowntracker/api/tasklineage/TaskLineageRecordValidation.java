package com.shutdowntracker.api.tasklineage;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class TaskLineageRecordValidation {

    private static final BigDecimal MIN_CONFIDENCE = BigDecimal.ZERO;
    private static final BigDecimal MAX_CONFIDENCE = new BigDecimal("100");

    private TaskLineageRecordValidation() {
    }

    static <T> T requireNonNull(T value, String message) {
        return Objects.requireNonNull(value, message);
    }

    static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    static void requireConfidence(BigDecimal value, String fieldName) {
        if (value != null && (value.compareTo(MIN_CONFIDENCE) < 0 || value.compareTo(MAX_CONFIDENCE) > 0)) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 100.");
        }
    }

    static Map<String, Object> immutableObjectMap(Map<String, Object> value, String fieldName) {
        if (value == null) {
            return Map.of();
        }

        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException(fieldName + " must not contain null keys.");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
