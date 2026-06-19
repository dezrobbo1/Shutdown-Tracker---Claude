package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableObjectMap;
import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireNonNull;
import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireText;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ExportPreviewLineCreateRequest(
        UUID importedTaskId,
        String sourceEntityType,
        UUID sourceEntityId,
        String fieldName,
        String newValue,
        UUID sourceActorUserId,
        OffsetDateTime sourceTimestamp,
        String reason,
        Map<String, Object> metadata
) {
    public ExportPreviewLineCreateRequest {
        requireNonNull(importedTaskId, "importedTaskId is required.");
        requireText(sourceEntityType, "sourceEntityType is required.");
        requireNonNull(sourceEntityId, "sourceEntityId is required.");
        requireText(fieldName, "fieldName is required.");
        ExportPreviewField.fromFieldName(fieldName);
        requireText(newValue, "newValue is required.");
        metadata = immutableObjectMap(metadata, "metadata");
    }
}
