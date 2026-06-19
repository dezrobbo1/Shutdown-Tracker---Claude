package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableNonEmptyList;
import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableObjectMap;
import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireNonNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ExportPreviewCreateRequest(
        UUID projectSnapshotId,
        List<ExportPreviewLineCreateRequest> lines,
        Map<String, Object> metadata
) {
    public ExportPreviewCreateRequest {
        requireNonNull(projectSnapshotId, "projectSnapshotId is required.");
        lines = immutableNonEmptyList(lines, "At least one export preview line is required.");
        metadata = immutableObjectMap(metadata, "metadata");
    }
}
