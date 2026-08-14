package com.shutdowntracker.projectimport.contract;

import java.util.Map;

/**
 * One resource read from a Microsoft Project file.
 *
 * <p>{@code rawData} carries values that are not yet modelled as columns, including the
 * standard Project {@code Group} field that Operational Categories resolve against.
 */
public record ParsedResource(
        String externalUid,
        String name,
        String resourceType,
        Map<String, Object> rawData
) {
    public ParsedResource {
        rawData = rawData == null ? Map.of() : Map.copyOf(rawData);
    }
}
