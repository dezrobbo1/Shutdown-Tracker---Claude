package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableObjectMap;

import java.util.Map;

/**
 * Verifying identity is deliberately absent: it comes from the authenticated request actor, not the body.
 */
public record ExportBatchVerificationRequest(
        String reason,
        Map<String, Object> metadata
) {
    public ExportBatchVerificationRequest {
        metadata = immutableObjectMap(metadata, "metadata");
    }

    public static ExportBatchVerificationRequest empty() {
        return new ExportBatchVerificationRequest(null, null);
    }
}
