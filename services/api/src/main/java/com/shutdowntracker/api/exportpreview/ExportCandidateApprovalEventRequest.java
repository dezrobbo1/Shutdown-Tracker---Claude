package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableObjectMap;
import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireNonNull;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * A decision recorded against one authoritative export candidate.
 *
 * <p>{@code reviewedByUserId} is nullable because a caller must not be able to assert it. The
 * controller overwrites whatever arrives with the resolved actor, so the identity bound to the
 * approval is always the authenticated reviewer rather than one the request named.
 */
public record ExportCandidateApprovalEventRequest(
        ApprovalState approvalState,
        OffsetDateTime requestedAt,
        UUID reviewedByUserId,
        OffsetDateTime reviewedAt,
        String reason,
        Map<String, Object> metadata
) {
    public ExportCandidateApprovalEventRequest {
        requireNonNull(approvalState, "approvalState is required.");
        if (requestedAt != null && reviewedAt != null && reviewedAt.isBefore(requestedAt)) {
            throw new IllegalArgumentException("reviewedAt must not precede requestedAt.");
        }
        metadata = immutableObjectMap(metadata, "metadata");
    }
}
