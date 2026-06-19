package com.shutdowntracker.api.exportpreview;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExportPreviewBatchRecord(
        UUID id,
        UUID projectId,
        UUID projectSnapshotId,
        ExportBatchState status,
        OffsetDateTime previewCreatedAt,
        OffsetDateTime approvedAt,
        UUID approvedByUserId,
        OffsetDateTime generatedAt,
        UUID generatedByUserId,
        OffsetDateTime verifiedAt,
        UUID verifiedByUserId,
        String exportFileUri,
        String exportFileHash,
        String failureReason,
        int lineCount,
        int eligibleLineCount,
        int ineligibleLineCount
) {
}
