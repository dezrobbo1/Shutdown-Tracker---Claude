package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableObjectMap;
import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireNonNull;

import java.util.Map;
import java.util.UUID;

public record ExportBatchVerificationRequest(
        UUID verifiedByUserId,
        String reason,
        Map<String, Object> metadata
) {
    public ExportBatchVerificationRequest {
        verifiedByUserId = requireNonNull(verifiedByUserId, "verifiedByUserId is required.");
        metadata = immutableObjectMap(metadata, "metadata");
    }
}
