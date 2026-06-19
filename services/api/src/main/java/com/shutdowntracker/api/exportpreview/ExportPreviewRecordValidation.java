package com.shutdowntracker.api.exportpreview;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ExportPreviewRecordValidation {

    private ExportPreviewRecordValidation() {
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

    static <T> List<T> immutableNonEmptyList(List<T> value, String message) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return List.copyOf(value);
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
