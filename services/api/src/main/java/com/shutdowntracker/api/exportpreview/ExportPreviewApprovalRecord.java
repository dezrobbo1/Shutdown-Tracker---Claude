package com.shutdowntracker.api.exportpreview;

import java.util.Objects;
import java.util.UUID;

public record ExportPreviewApprovalRecord(
        UUID id,
        ApprovalState approvalState
) {
    public ExportPreviewApprovalRecord {
        Objects.requireNonNull(id, "id is required.");
        Objects.requireNonNull(approvalState, "approvalState is required.");
    }
}
