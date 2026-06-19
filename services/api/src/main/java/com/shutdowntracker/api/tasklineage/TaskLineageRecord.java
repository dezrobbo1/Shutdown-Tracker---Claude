package com.shutdowntracker.api.tasklineage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TaskLineageRecord(
        UUID id,
        UUID projectId,
        UUID previousSnapshotId,
        UUID currentSnapshotId,
        UUID previousImportedTaskId,
        String previousTaskExternalUid,
        String previousTaskName,
        UUID currentImportedTaskId,
        String currentTaskExternalUid,
        String currentTaskName,
        String matchMethod,
        BigDecimal matchConfidence,
        TaskLineageReviewState reviewState,
        UUID reviewedByUserId,
        OffsetDateTime reviewedAt
) {
}
