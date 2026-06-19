package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableObjectMap;
import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireNonNull;

import java.util.Map;
import java.util.UUID;

public record ExportBatchProjectOpenRequest(
        UUID openedByUserId,
        String reason,
        Map<String, Object> metadata
) {
    public ExportBatchProjectOpenRequest {
        openedByUserId = requireNonNull(openedByUserId, "openedByUserId is required.");
        metadata = immutableObjectMap(metadata, "metadata");
    }
}
