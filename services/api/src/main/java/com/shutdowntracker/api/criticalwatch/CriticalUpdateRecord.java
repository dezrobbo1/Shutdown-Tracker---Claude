package com.shutdowntracker.api.criticalwatch;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A submitted Critical Work Package report. Corrections supersede rather than overwrite. */
public record CriticalUpdateRecord(
        UUID id,
        UUID projectId,
        UUID criticalWorkPackageId,
        String status,
        String updateMode,
        OffsetDateTime submittedAt,
        UUID submittedByUserId,
        String currentFocus,
        String currentBlockerSummary,
        String nextTarget,
        UUID supersedesCriticalUpdateId
) {
}
