package com.shutdowntracker.api.importedproject;

import java.util.Map;
import java.util.UUID;

public record ImportedAssignmentCreateRequest(
        String externalUid,
        String taskExternalUid,
        String resourceExternalUid,
        UUID importedTaskId,
        UUID importedResourceId,
        Map<String, Object> rawData
) {
    public ImportedAssignmentCreateRequest {
        rawData = ImportedProjectRecordValidation.immutableObjectMap(rawData, "rawData");
    }
}
