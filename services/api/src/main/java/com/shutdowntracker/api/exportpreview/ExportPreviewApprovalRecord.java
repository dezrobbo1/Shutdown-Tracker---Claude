package com.shutdowntracker.api.exportpreview;

import java.util.Objects;
import java.util.UUID;

public record ExportPreviewApprovalRecord(
        UUID id,
        ApprovalState approvalState,
        UUID authoritativeExportCandidateId,
        Integer candidateBindingPolicyVersion
) {
    public ExportPreviewApprovalRecord {
        Objects.requireNonNull(id, "id is required.");
        Objects.requireNonNull(approvalState, "approvalState is required.");
    }

    public ExportPreviewApprovalRecord(UUID id, ApprovalState approvalState) {
        this(id, approvalState, null, null);
    }
}
