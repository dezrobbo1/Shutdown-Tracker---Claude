package com.shutdowntracker.api.importedproject;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ProjectSnapshotCreateRequest(
        UUID projectId,
        UUID importBatchId,
        ProjectSnapshotStatus status,
        String externalProjectUid,
        String externalProjectName,
        OffsetDateTime projectStatusDate,
        Map<String, Object> metadata
) {
    public ProjectSnapshotCreateRequest {
        projectId = ImportedProjectRecordValidation.requireNonNull(projectId, "projectId is required.");
        importBatchId = ImportedProjectRecordValidation.requireNonNull(importBatchId, "importBatchId is required.");
        status = ImportedProjectRecordValidation.requireNonNull(status, "status is required.");
        externalProjectName = ImportedProjectRecordValidation.requireText(
                externalProjectName,
                "externalProjectName is required."
        );
        metadata = ImportedProjectRecordValidation.immutableObjectMap(metadata, "metadata");
    }
}
