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
        Map<String, Object> clientMetadata,
        Map<String, Object> provenance
) {
    public ExportBatchGeneratedRequest {
        exportFileUri = requireText(exportFileUri, "exportFileUri is required.");
        exportFileHash = requireText(exportFileHash, "exportFileHash is required.");
        clientMetadata = immutableObjectMap(clientMetadata, "clientMetadata");
        provenance = immutableObjectMap(provenance, "provenance");
    }

    public ExportBatchGeneratedRequest(
            String exportFileUri,
            String exportFileHash,
            UUID generatedByUserId,
            String reason,
            Map<String, Object> clientMetadata
    ) {
        this(exportFileUri, exportFileHash, generatedByUserId, reason, clientMetadata, Map.of());
    }
}
