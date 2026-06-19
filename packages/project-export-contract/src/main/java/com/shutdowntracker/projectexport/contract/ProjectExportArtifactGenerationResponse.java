package com.shutdowntracker.projectexport.contract;

import java.util.Objects;
import java.util.UUID;

public record ProjectExportArtifactGenerationResponse(
        UUID exportBatchId,
        UUID projectId,
        String exportFileUri,
        String exportFileHash,
        ProjectExportArtifactSummary artifactSummary,
        String message
) {
    public ProjectExportArtifactGenerationResponse {
        Objects.requireNonNull(exportBatchId, "exportBatchId is required.");
        Objects.requireNonNull(projectId, "projectId is required.");
        if (exportFileUri == null || exportFileUri.isBlank()) {
            throw new IllegalArgumentException("exportFileUri is required.");
        }
        if (exportFileHash == null || exportFileHash.isBlank()) {
            throw new IllegalArgumentException("exportFileHash is required.");
        }
        Objects.requireNonNull(artifactSummary, "artifactSummary is required.");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required.");
        }
    }
}
