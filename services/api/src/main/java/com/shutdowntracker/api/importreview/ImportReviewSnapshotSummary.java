package com.shutdowntracker.api.importreview;

import com.shutdowntracker.api.importedproject.ProjectSnapshotStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportReviewSnapshotSummary(
        UUID id,
        UUID projectId,
        UUID importBatchId,
        ProjectSnapshotStatus status,
        String externalProjectUid,
        String externalProjectName,
        OffsetDateTime projectStatusDate,
        int snapshotVersion,
        String parserName,
        String parserVersion,
        int warningCount,
        int errorCount,
        int taskCount,
        int summaryTaskCount,
        int leafTaskCount,
        int resourceCount,
        int assignmentCount,
        int extendedAttributeCount
) {
}
