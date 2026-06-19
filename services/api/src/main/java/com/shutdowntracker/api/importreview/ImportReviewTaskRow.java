package com.shutdowntracker.api.importreview;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportReviewTaskRow(
        UUID id,
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
        String notes
) {
}
