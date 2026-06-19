package com.shutdowntracker.api.exportpreview.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LocalExportArtifactStorage implements ExportArtifactStorage {

    private static final String STORAGE_KIND = "local_filesystem";

    private final ExportArtifactStorageProperties properties;

    public LocalExportArtifactStorage(ExportArtifactStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public ExportArtifactStorageLocation prepareExportArtifact(UUID projectId, UUID exportBatchId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId is required.");
        }
        if (exportBatchId == null) {
            throw new IllegalArgumentException("exportBatchId is required.");
        }

        Path root = properties.localRoot().toAbsolutePath().normalize();
        String artifactFilename = exportBatchId + ".mspdi.xml";
        Path directory = root.resolve(projectId.toString()).normalize();
        Path outputPath = directory.resolve(artifactFilename).normalize();

        if (!outputPath.startsWith(root)) {
            throw new IllegalStateException("Export artifact storage target escaped the configured local root.");
        }

        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare export artifact storage directory.", exception);
        }

        return new ExportArtifactStorageLocation(
                projectId,
                exportBatchId,
                artifactFilename,
                outputPath,
                outputPath.toUri().toString(),
                STORAGE_KIND
        );
    }
}
