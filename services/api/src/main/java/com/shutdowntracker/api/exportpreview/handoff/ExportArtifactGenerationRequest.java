package com.shutdowntracker.api.exportpreview.handoff;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ExportArtifactGenerationRequest(
        UUID generatedByUserId,
        String reason,
        Map<String, Object> metadata
) {
    public ExportArtifactGenerationRequest {
        metadata = immutableObjectMap(metadata);
    }

    public static ExportArtifactGenerationRequest empty() {
        return new ExportArtifactGenerationRequest(null, null, null);
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
