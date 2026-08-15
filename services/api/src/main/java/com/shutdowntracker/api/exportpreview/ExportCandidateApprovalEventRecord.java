package com.shutdowntracker.api.exportpreview;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ExportCandidateApprovalEventRecord(
        UUID id,
        UUID projectId,
        UUID projectSnapshotId,
        UUID authoritativeExportCandidateId,
        Integer candidateBindingPolicyVersion,
        ApprovalState approvalState,
        UUID requestedByUserId,
        OffsetDateTime requestedAt,
        UUID reviewedByUserId,
        OffsetDateTime reviewedAt,
        String reason,
        OffsetDateTime createdAt,
        Map<String, Object> metadata
) {
    public ExportCandidateApprovalEventRecord {
        metadata = ExportPreviewRecordValidation.immutableObjectMap(metadata, "metadata");
    }
}
