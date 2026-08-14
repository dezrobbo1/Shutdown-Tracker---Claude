package com.shutdowntracker.projectimport.contract;

import java.util.Map;

/**
 * One resource assignment read from a Microsoft Project file.
 *
 * <p>Both sides of the assignment travel as external identifiers. The API resolves them
 * to stored task and resource rows while persisting the snapshot.
 */
public record ParsedAssignment(
        String externalUid,
        String taskExternalUid,
        String resourceExternalUid,
        Map<String, Object> rawData
) {
    public ParsedAssignment {
        rawData = rawData == null ? Map.of() : Map.copyOf(rawData);
    }
}
