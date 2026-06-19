package com.shutdowntracker.projectimport.contract;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ProjectParseSummaryResponse(
        UUID importBatchId,
        String parserName,
        String parserVersion,
        String sourceFilename,
        String detectedFormat,
        String projectName,
        int taskCount,
        int summaryTaskCount,
        int leafTaskCount,
        int resourceCount,
        int assignmentCount,
        int calendarCount,
        int customFieldCount,
        int warningCount,
        int errorCount,
        List<String> notes
) {
    public ProjectParseSummaryResponse {
        Objects.requireNonNull(importBatchId, "importBatchId is required.");
        parserName = defaultText(parserName);
        parserVersion = defaultText(parserVersion);
        sourceFilename = defaultText(sourceFilename);
        detectedFormat = defaultText(detectedFormat);
        projectName = defaultText(projectName);
        requireNonNegative(taskCount, "taskCount");
        requireNonNegative(summaryTaskCount, "summaryTaskCount");
        requireNonNegative(leafTaskCount, "leafTaskCount");
        requireNonNegative(resourceCount, "resourceCount");
        requireNonNegative(assignmentCount, "assignmentCount");
        requireNonNegative(calendarCount, "calendarCount");
        requireNonNegative(customFieldCount, "customFieldCount");
        requireNonNegative(warningCount, "warningCount");
        requireNonNegative(errorCount, "errorCount");
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    private static String defaultText(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value;
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative.");
        }
    }
}
