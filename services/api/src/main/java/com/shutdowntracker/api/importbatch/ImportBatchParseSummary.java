package com.shutdowntracker.api.importbatch;

import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.util.List;
import java.util.Objects;

public record ImportBatchParseSummary(
        String sourceFilename,
        String detectedFormat,
        String projectName,
        ImportBatchParseSummaryCounts counts,
        boolean summaryOnly,
        List<String> notes
) {
    public ImportBatchParseSummary {
        sourceFilename = requireText(sourceFilename, "sourceFilename is required.");
        detectedFormat = requireText(detectedFormat, "detectedFormat is required.");
        projectName = requireText(projectName, "projectName is required.");
        Objects.requireNonNull(counts, "counts is required.");
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public static ImportBatchParseSummary from(ProjectParseSummaryResponse response) {
        Objects.requireNonNull(response, "response is required.");
        return new ImportBatchParseSummary(
                response.sourceFilename(),
                response.detectedFormat(),
                response.projectName(),
                new ImportBatchParseSummaryCounts(
                        response.taskCount(),
                        response.summaryTaskCount(),
                        response.leafTaskCount(),
                        response.resourceCount(),
                        response.assignmentCount(),
                        response.calendarCount(),
                        response.customFieldCount()
                ),
                true,
                response.notes()
        );
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
