package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableObjectMap;

import java.util.Map;

/**
 * Opening identity is deliberately absent: it comes from the authenticated request actor, not the body.
 */
public record ExportBatchProjectOpenRequest(
        String reason,
        Map<String, Object> metadata
) {
    public ExportBatchProjectOpenRequest {
        metadata = immutableObjectMap(metadata, "metadata");
    }

    public static ExportBatchProjectOpenRequest empty() {
        return new ExportBatchProjectOpenRequest(null, null);
    }
}
