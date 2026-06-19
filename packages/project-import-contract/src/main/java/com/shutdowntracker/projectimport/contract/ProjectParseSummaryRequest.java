package com.shutdowntracker.projectimport.contract;

import java.util.Objects;
import java.util.UUID;

public record ProjectParseSummaryRequest(
        UUID importBatchId,
        UUID projectId,
        UUID sourceFileId,
        String storageUri,
        String originalFilename
) {
    public ProjectParseSummaryRequest {
        Objects.requireNonNull(importBatchId, "importBatchId is required.");
        Objects.requireNonNull(projectId, "projectId is required.");
        Objects.requireNonNull(sourceFileId, "sourceFileId is required.");
        storageUri = requireText(storageUri, "storageUri is required.");
        originalFilename = requireText(originalFilename, "originalFilename is required.");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
