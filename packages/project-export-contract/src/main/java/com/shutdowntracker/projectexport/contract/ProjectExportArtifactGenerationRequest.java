package com.shutdowntracker.projectexport.contract;

import java.util.Objects;
import java.util.UUID;

public record ProjectExportArtifactGenerationRequest(
        UUID exportBatchId,
        UUID projectId,
        String outputPath,
        ProjectExportArtifactRequest artifactRequest
) {
    public ProjectExportArtifactGenerationRequest {
        Objects.requireNonNull(exportBatchId, "exportBatchId is required.");
        Objects.requireNonNull(projectId, "projectId is required.");
        if (outputPath == null || outputPath.isBlank()) {
            throw new IllegalArgumentException("outputPath is required.");
        }
        Objects.requireNonNull(artifactRequest, "artifactRequest is required.");
    }
}
