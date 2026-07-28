package com.shutdowntracker.api.exportpreview;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ExportCandidateRecord(
        UUID id,
        Integer bindingPolicyVersion,
        UUID projectId,
        UUID projectSnapshotId,
        UUID importedTaskId,
        String sourceEntityType,
        UUID sourceEntityId,
        String sourceVersion,
        String fieldName,
        String normalizedOldValue,
        String normalizedNewValue,
        String sourceEventOrPayloadHash,
        String capturedTaskExternalUid,
        String capturedTaskExternalId,
        String capturedTaskName,
        boolean capturedLeafTask,
        UUID sourceActorUserId,
        OffsetDateTime sourceTimestamp,
        String reason,
        OffsetDateTime createdAt,
        Map<String, Object> metadata
) {
    public ExportCandidateRecord {
        metadata = ExportPreviewRecordValidation.immutableObjectMap(metadata, "metadata");
    }
}
