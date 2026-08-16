package com.shutdowntracker.api.execution;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One structured progress submission and the independent review states applied to it. */
public record TaskProgressUpdateRecord(
        UUID id,
        UUID projectId,
        UUID projectSnapshotId,
        UUID importedTaskId,
        TaskExecutionState executionState,
        BigDecimal percentComplete,
        OffsetDateTime actualStart,
        OffsetDateTime actualFinish,
        BigDecimal physicalPercentComplete,
        String comment,
        UUID submittedByUserId,
        ProgressReviewState progressReviewState,
        PlannerReviewState plannerReviewState,
        ProgressExportState exportState,
        UUID supersedesProgressUpdateId
) {
}
