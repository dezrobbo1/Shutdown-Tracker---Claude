package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableObjectMap;
import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireText;

import java.util.Map;
import java.util.UUID;

public record ExportBatchGeneratedRequest(
        String exportFileUri,
        String exportFileHash,
        UUID generatedByUserId,
        String reason,
        Map<String, Object> metadata
) {
    public ExportBatchGeneratedRequest {
        exportFileUri = requireText(exportFileUri, "exportFileUri is required.");
        exportFileHash = requireText(exportFileHash, "exportFileHash is required.");
        metadata = immutableObjectMap(metadata, "metadata");
    }
}
