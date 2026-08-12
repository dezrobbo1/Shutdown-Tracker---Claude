package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableObjectMap;

import java.util.Map;

/**
 * Reviewer identity is deliberately absent: it comes from the authenticated request actor, not the body.
 */
public record ExportBatchDecisionRequest(
        String reason,
        Map<String, Object> metadata
) {
    public ExportBatchDecisionRequest {
        metadata = immutableObjectMap(metadata, "metadata");
    }

    public static ExportBatchDecisionRequest empty() {
        return new ExportBatchDecisionRequest(null, null);
    }
}
