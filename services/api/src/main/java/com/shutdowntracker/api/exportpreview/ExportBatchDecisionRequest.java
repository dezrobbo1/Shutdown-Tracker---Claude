package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableObjectMap;

import java.util.Map;
import java.util.UUID;

public record ExportBatchDecisionRequest(
        UUID reviewedByUserId,
        String reason,
        Map<String, Object> metadata
) {
    public ExportBatchDecisionRequest {
        metadata = immutableObjectMap(metadata, "metadata");
    }

    public static ExportBatchDecisionRequest empty() {
        return new ExportBatchDecisionRequest(null, null, null);
    }
}
