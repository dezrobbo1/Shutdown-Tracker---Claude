package com.shutdowntracker.api.importedproject;

import java.util.UUID;

public record ImportedAssignmentRecord(
        UUID id,
        UUID projectId,
        UUID projectSnapshotId,
        String externalUid,
        String taskExternalUid,
        String resourceExternalUid
) {
}
