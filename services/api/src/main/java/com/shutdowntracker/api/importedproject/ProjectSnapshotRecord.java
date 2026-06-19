package com.shutdowntracker.api.importedproject;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProjectSnapshotRecord(
        UUID id,
        UUID projectId,
        UUID importBatchId,
        ProjectSnapshotStatus status,
        String externalProjectUid,
        String externalProjectName,
        OffsetDateTime projectStatusDate,
        int snapshotVersion
) {
}
