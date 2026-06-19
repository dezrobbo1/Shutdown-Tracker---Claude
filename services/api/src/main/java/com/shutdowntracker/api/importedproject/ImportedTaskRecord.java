package com.shutdowntracker.api.importedproject;

import java.util.UUID;

public record ImportedTaskRecord(
        UUID id,
        UUID projectId,
        UUID projectSnapshotId,
        String externalUid,
        String name,
        boolean summary
) {
}
