package com.shutdowntracker.api.importedproject;

import java.util.UUID;

public record ImportedExtendedAttributeRecord(
        UUID id,
        UUID projectId,
        UUID projectSnapshotId,
        ImportedExtendedAttributeEntityType entityType,
        String entityExternalUid,
        String fieldId,
        String fieldName
) {
}
