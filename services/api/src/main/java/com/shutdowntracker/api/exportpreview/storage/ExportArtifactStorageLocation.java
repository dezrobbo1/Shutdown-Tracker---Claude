package com.shutdowntracker.api.exportpreview.storage;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public record ExportArtifactStorageLocation(
        UUID projectId,
        UUID exportBatchId,
        String artifactFilename,
        Path outputPath,
        String storageUri,
        String storageKind
) {

    public ExportArtifactStorageLocation {
        Objects.requireNonNull(projectId, "projectId is required.");
        Objects.requireNonNull(exportBatchId, "exportBatchId is required.");
        if (artifactFilename == null || artifactFilename.isBlank()) {
            throw new IllegalArgumentException("artifactFilename is required.");
        }
        if (!artifactFilename.toLowerCase().endsWith(".xml")) {
            throw new IllegalArgumentException("artifactFilename must end with .xml.");
        }
        Objects.requireNonNull(outputPath, "outputPath is required.");
        if (storageUri == null || storageUri.isBlank()) {
            throw new IllegalArgumentException("storageUri is required.");
        }
        if (storageKind == null || storageKind.isBlank()) {
            throw new IllegalArgumentException("storageKind is required.");
        }
    }
}
