package com.shutdowntracker.api.importbatch;

import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.util.Objects;
import java.util.UUID;

public record ImportBatchParseSummaryUpdate(
        UUID importBatchId,
        String parserName,
        String parserVersion,
        int warningCount,
        int errorCount,
        ImportBatchParseSummary parseSummary
) {
    public ImportBatchParseSummaryUpdate {
        Objects.requireNonNull(importBatchId, "importBatchId is required.");
        parserName = requireText(parserName, "parserName is required.");
        parserVersion = requireText(parserVersion, "parserVersion is required.");
        requireNonNegative(warningCount, "warningCount");
        requireNonNegative(errorCount, "errorCount");
        Objects.requireNonNull(parseSummary, "parseSummary is required.");
    }

    public static ImportBatchParseSummaryUpdate from(ProjectParseSummaryResponse response) {
        Objects.requireNonNull(response, "response is required.");
        return new ImportBatchParseSummaryUpdate(
                response.importBatchId(),
                response.parserName(),
                response.parserVersion(),
                response.warningCount(),
                response.errorCount(),
                ImportBatchParseSummary.from(response)
        );
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative.");
        }
    }
}
