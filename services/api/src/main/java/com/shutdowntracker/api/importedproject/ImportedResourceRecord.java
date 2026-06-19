package com.shutdowntracker.api.importedproject;

import java.util.UUID;

public record ImportedResourceRecord(
        UUID id,
        UUID projectId,
        UUID projectSnapshotId,
        String externalUid,
        String name,
        String resourceType
) {
}
