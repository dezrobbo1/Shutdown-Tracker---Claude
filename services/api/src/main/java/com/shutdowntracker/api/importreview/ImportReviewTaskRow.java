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
        String notes,
        /**
         * Duration as Microsoft Project renders it — "5 days", "8 hrs" — not a number.
         *
         * <p>MPXJ carries duration as a value and a unit, and the importer stores only the
         * formatted text. That is enough to show the column and enough for the round trip, which
         * never writes duration back. Filtering or sorting by duration would need the number and
         * the unit stored separately, and is deliberately not offered rather than offered
         * incorrectly against a string.
         */
        String durationText
) {
}
