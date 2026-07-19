package com.shutdowntracker.api.exportpreview;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExportCandidateRecord(
        UUID id,
        UUID approvalRecordId,
        Integer bindingPolicyVersion,
        UUID projectId,
        UUID projectSnapshotId,
        UUID importedTaskId,
        String sourceEntityType,
        UUID sourceEntityId,
        ApprovalState approvalState,
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
        String reason
) {
}
