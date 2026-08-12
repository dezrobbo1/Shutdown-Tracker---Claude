package com.shutdowntracker.api.approval;

import com.shutdowntracker.api.exportpreview.ApprovalState;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Reviewer identity is deliberately absent: it comes from the authenticated request actor, not the body.
 */
public record ApprovalRecordCreateRequest(
        String sourceEntityType,
        UUID sourceEntityId,
        ApprovalState approvalState,
        String reason,
        Map<String, Object> metadata
) {

    public ApprovalRecordCreateRequest {
        sourceEntityType = requireText(sourceEntityType, "sourceEntityType is required.");
        Objects.requireNonNull(sourceEntityId, "sourceEntityId is required.");
        Objects.requireNonNull(approvalState, "approvalState is required.");
        metadata = immutableObjectMap(metadata);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static Map<String, Object> immutableObjectMap(Map<String, Object> value) {
        if (value == null) {
            return Map.of();
        }

        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("metadata must not contain null keys.");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
