package com.shutdowntracker.api.importedproject;

import java.util.Map;

public record ImportedExtendedAttributeCreateRequest(
        ImportedExtendedAttributeEntityType entityType,
        String entityExternalUid,
        String fieldId,
        String fieldName,
        String alias,
        String value,
        Map<String, Object> rawData
) {
    public ImportedExtendedAttributeCreateRequest {
        entityType = ImportedProjectRecordValidation.requireNonNull(entityType, "entityType is required.");
        rawData = ImportedProjectRecordValidation.immutableObjectMap(rawData, "rawData");
    }
}
