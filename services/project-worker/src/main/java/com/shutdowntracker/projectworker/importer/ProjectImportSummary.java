package com.shutdowntracker.projectworker.importer;

import java.util.List;

public record ProjectImportSummary(
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
        List<String> notes
) {
    public ProjectImportSummary {
        notes = List.copyOf(notes);
    }
}
