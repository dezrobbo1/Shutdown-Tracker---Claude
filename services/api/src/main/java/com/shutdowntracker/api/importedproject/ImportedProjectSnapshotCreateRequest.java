package com.shutdowntracker.api.importedproject;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ImportedProjectSnapshotCreateRequest(
        UUID projectId,
        UUID importBatchId,
        String externalProjectUid,
        String externalProjectName,
        OffsetDateTime projectStatusDate,
        Map<String, Object> metadata,
        ImportedProjectEntities entities
) {
    public ImportedProjectSnapshotCreateRequest {
        projectId = ImportedProjectRecordValidation.requireNonNull(projectId, "projectId is required.");
        importBatchId = ImportedProjectRecordValidation.requireNonNull(importBatchId, "importBatchId is required.");
        externalProjectName = ImportedProjectRecordValidation.requireText(
                externalProjectName,
                "externalProjectName is required."
        );
        metadata = ImportedProjectRecordValidation.immutableObjectMap(metadata, "metadata");
        entities = entities == null ? ImportedProjectEntities.empty() : entities;
    }
}
