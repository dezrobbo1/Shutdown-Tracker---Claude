package com.shutdowntracker.api.approval;

import com.shutdowntracker.api.exportpreview.ApprovalState;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A review decision on an operational record that may become an export candidate.
 *
 * <p>Approval records are the gate the export preview reads: a line is export-eligible only when the
 * latest approval for its source entity is {@code approved_for_export} and the imported task is a leaf.
 *
 * <p>Approval of a source record is not export batch approval. Those are separate decisions with separate
 * states, per docs/product/approval-export-state-model.md.
 */
public record ApprovalRecord(
        UUID id,
        UUID projectId,
        String sourceEntityType,
        UUID sourceEntityId,
        ApprovalState approvalState,
        UUID requestedByUserId,
        OffsetDateTime requestedAt,
        UUID reviewedByUserId,
        OffsetDateTime reviewedAt,
        String reason
) {
}
