package com.shutdowntracker.api.operations;

import java.time.OffsetDateTime;
import java.util.UUID;

public record HandoverNoteRecord(
        UUID id,
        UUID projectId,
        UUID importedTaskId,
        UUID problemId,
        String shiftLabel,
        String note,
        boolean requiresAcknowledgement,
        UUID createdByUserId,
        UUID acknowledgedByUserId,
        OffsetDateTime acknowledgedAt
) {
}
