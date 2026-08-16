package com.shutdowntracker.api.execution;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A field submission.
 *
 * <p>The submitter is not carried here. Attribution is resolved from the authenticated
 * request, so a caller cannot claim to be someone else.
 */
public record TaskProgressSubmitRequest(
        UUID importedTaskId,
        TaskExecutionState executionState,
        BigDecimal percentComplete,
        OffsetDateTime actualStart,
        OffsetDateTime actualFinish,
        BigDecimal physicalPercentComplete,
        String comment,
        String idempotencyKey,
        String offlineLocalId,
        UUID supersedesProgressUpdateId
) {
    public TaskProgressSubmitRequest {
        Objects.requireNonNull(importedTaskId, "importedTaskId is required.");
        Objects.requireNonNull(executionState, "executionState is required.");
        requirePercent(percentComplete, "percentComplete");
        requirePercent(physicalPercentComplete, "physicalPercentComplete");

        if (actualStart != null && actualFinish != null && actualFinish.isBefore(actualStart)) {
            throw new IllegalArgumentException("actualFinish must not be before actualStart.");
        }
        if (executionState.requiresReason() && (comment == null || comment.isBlank())) {
            throw new IllegalArgumentException(
                    "A " + executionState.databaseValue() + " update must say why.");
        }
        if (comment != null && comment.isBlank()) {
            comment = null;
        }
        if (idempotencyKey != null && idempotencyKey.isBlank()) {
            idempotencyKey = null;
        }
        if (offlineLocalId != null && offlineLocalId.isBlank()) {
            offlineLocalId = null;
        }
    }

    /**
     * Whether this submission carries any of the three fields on the MVP export whitelist.
     * Updates carrying none of them never need a planner decision.
     */
    public boolean carriesExportCandidateFields() {
        return percentComplete != null || actualStart != null || actualFinish != null;
    }

    private static void requirePercent(BigDecimal value, String fieldName) {
        if (value == null) {
            return;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 100.");
        }
    }
}
