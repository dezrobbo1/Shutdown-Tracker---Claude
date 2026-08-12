package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableObjectMap;
import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireText;

import java.util.Map;

/**
 * Generating identity is deliberately absent: it comes from the authenticated request actor, not the body.
 */
public record ExportBatchGeneratedRequest(
        String exportFileUri,
        String exportFileHash,
        String reason,
        Map<String, Object> metadata
) {
    public ExportBatchGeneratedRequest {
        exportFileUri = requireText(exportFileUri, "exportFileUri is required.");
        exportFileHash = requireText(exportFileHash, "exportFileHash is required.");
        metadata = immutableObjectMap(metadata, "metadata");
    }
}
