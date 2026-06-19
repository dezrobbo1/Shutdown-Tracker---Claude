package com.shutdowntracker.api.importbatch;

public record ImportBatchParseSummaryCounts(
        int taskCount,
        int summaryTaskCount,
        int leafTaskCount,
        int resourceCount,
        int assignmentCount,
        int calendarCount,
        int customFieldCount
) {
    public ImportBatchParseSummaryCounts {
        requireNonNegative(taskCount, "taskCount");
        requireNonNegative(summaryTaskCount, "summaryTaskCount");
        requireNonNegative(leafTaskCount, "leafTaskCount");
        requireNonNegative(resourceCount, "resourceCount");
        requireNonNegative(assignmentCount, "assignmentCount");
        requireNonNegative(calendarCount, "calendarCount");
        requireNonNegative(customFieldCount, "customFieldCount");
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative.");
        }
    }
}
