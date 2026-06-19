package com.shutdowntracker.api.importedproject;

import java.util.Map;

public record ImportedResourceCreateRequest(
        String externalUid,
        String name,
        String resourceType,
        Map<String, Object> rawData
) {
    public ImportedResourceCreateRequest {
        rawData = ImportedProjectRecordValidation.immutableObjectMap(rawData, "rawData");
    }
}
