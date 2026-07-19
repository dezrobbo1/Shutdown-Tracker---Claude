package com.shutdowntracker.api.exportpreview;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExportPreviewLineRecord(
        UUID id,
        UUID exportBatchId,
        UUID projectId,
        UUID projectSnapshotId,
        UUID importedTaskId,
        String importedTaskExternalUid,
        String importedTaskExternalId,
        String importedTaskName,
        String sourceEntityType,
        UUID sourceEntityId,
        ApprovalState approvalState,
        UUID sourceApprovalRecordId,
        String fieldName,
        String oldValue,
        String newValue,
        UUID sourceActorUserId,
        OffsetDateTime sourceTimestamp,
        String reason,
        boolean leafTask,
        boolean exportEligible,
        Integer integrityPolicyVersion,
        UUID authoritativeExportCandidateId,
        String capturedSourceEventOrPayloadHash
) {
}
