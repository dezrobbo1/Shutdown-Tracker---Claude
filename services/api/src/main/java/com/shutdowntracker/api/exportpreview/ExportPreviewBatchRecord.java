package com.shutdowntracker.api.exportpreview;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExportPreviewBatchRecord(
        UUID id,
        UUID projectId,
        UUID projectSnapshotId,
        ExportBatchState status,
        OffsetDateTime previewCreatedAt,
        int lineCount,
        int eligibleLineCount,
        int ineligibleLineCount
) {
}
