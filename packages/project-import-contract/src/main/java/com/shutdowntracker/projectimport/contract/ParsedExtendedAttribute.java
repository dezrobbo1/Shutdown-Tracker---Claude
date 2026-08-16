package com.shutdowntracker.projectimport.contract;

import java.util.Map;

/**
 * One custom field or extended attribute value read from a Microsoft Project file.
 *
 * <p>These carry the planner's own classifications (Text1, Number3, and similar), which
 * Operational Categories later map to named categories. The value is stored exactly as
 * imported; aliases and roll-ups are Shutdown Tracker configuration applied on top.
 */
public record ParsedExtendedAttribute(
        String entityType,
        String entityExternalUid,
        String fieldId,
        String fieldName,
        String alias,
        String value,
        Map<String, Object> rawData
) {
    public ParsedExtendedAttribute {
        entityType = entityType == null || entityType.isBlank() ? "other" : entityType;
        rawData = rawData == null ? Map.of() : Map.copyOf(rawData);
    }
}
