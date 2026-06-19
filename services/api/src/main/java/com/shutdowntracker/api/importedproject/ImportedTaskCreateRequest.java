package com.shutdowntracker.api.importedproject;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ImportedTaskCreateRequest(
        String externalUid,
        String externalId,
        String name,
        String wbs,
        String outlineNumber,
        Integer outlineLevel,
        boolean summary,
        String parentExternalUid,
        UUID parentImportedTaskId,
        OffsetDateTime plannedStart,
        OffsetDateTime plannedFinish,
        OffsetDateTime actualStart,
        OffsetDateTime actualFinish,
        BigDecimal percentComplete,
        BigDecimal physicalPercentComplete,
        String notes,
        Map<String, Object> rawData
) {
    public ImportedTaskCreateRequest {
        name = ImportedProjectRecordValidation.requireText(name, "name is required.");
        ImportedProjectRecordValidation.requireNonNegative(outlineLevel, "outlineLevel");
        ImportedProjectRecordValidation.requirePercent(percentComplete, "percentComplete");
        ImportedProjectRecordValidation.requirePercent(physicalPercentComplete, "physicalPercentComplete");
        ImportedProjectRecordValidation.requireOrderedDates(
                plannedStart,
                plannedFinish,
                "plannedFinish must not be before plannedStart."
        );
        ImportedProjectRecordValidation.requireOrderedDates(
                actualStart,
                actualFinish,
                "actualFinish must not be before actualStart."
        );
        rawData = ImportedProjectRecordValidation.immutableObjectMap(rawData, "rawData");
    }
}
