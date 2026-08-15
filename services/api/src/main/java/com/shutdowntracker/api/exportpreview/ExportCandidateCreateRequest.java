package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableObjectMap;
import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireNonNull;
import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireText;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ExportCandidateCreateRequest(
        UUID projectSnapshotId,
        UUID importedTaskId,
        String fieldName,
        String proposedValue,
        String sourceEntityType,
        UUID sourceEntityId,
        String sourceVersion,
        UUID sourceActorUserId,
        OffsetDateTime sourceTimestamp,
        String reason,
        Map<String, Object> metadata
) {
    public ExportCandidateCreateRequest {
        requireNonNull(projectSnapshotId, "projectSnapshotId is required.");
        requireNonNull(importedTaskId, "importedTaskId is required.");
        fieldName = requireText(fieldName, "fieldName is required.");
        proposedValue = requireText(proposedValue, "proposedValue is required.");
        sourceEntityType = requireText(sourceEntityType, "sourceEntityType is required.");
        requireNonNull(sourceEntityId, "sourceEntityId is required.");
        sourceVersion = requireText(sourceVersion, "sourceVersion is required.");
        metadata = immutableObjectMap(metadata, "metadata");
    }
}
