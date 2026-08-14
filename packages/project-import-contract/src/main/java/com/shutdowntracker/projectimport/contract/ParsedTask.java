package com.shutdowntracker.projectimport.contract;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * One task read from a Microsoft Project file.
 *
 * <p>Every value is reported exactly as the parser found it. Shutdown Tracker does not
 * calculate schedule values, so nothing here is derived, recalculated, or defaulted to a
 * computed result.
 *
 * <p>Hierarchy travels as {@link #parentExternalUid()} rather than a database identifier:
 * the worker has no knowledge of Shutdown Tracker's primary keys, so the API resolves
 * parents as it persists.
 */
public record ParsedTask(
        String externalUid,
        String externalId,
        String name,
        String wbs,
        String outlineNumber,
        Integer outlineLevel,
        boolean summary,
        String parentExternalUid,
        OffsetDateTime plannedStart,
        OffsetDateTime plannedFinish,
        OffsetDateTime actualStart,
        OffsetDateTime actualFinish,
        BigDecimal percentComplete,
        BigDecimal physicalPercentComplete,
        String notes,
        Map<String, Object> rawData
) {
    public ParsedTask {
        name = name == null || name.isBlank() ? "(unnamed task)" : name;
        rawData = rawData == null ? Map.of() : Map.copyOf(rawData);
    }
}
