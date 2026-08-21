package com.shutdowntracker.api.exportpreview.storage;

import com.shutdowntracker.api.storage.LocalFileStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LocalExportArtifactStorage implements ExportArtifactStorage {

    private static final String STORAGE_KIND = "local_filesystem";

    private final ExportArtifactStorageProperties properties;
    private final LocalFileStore fileStore;

    public LocalExportArtifactStorage(ExportArtifactStorageProperties properties) {
        this.properties = properties;
        this.fileStore = new LocalFileStore(
                properties.localRoot(),
                "Export artifact storage",
                "generated export artifact",
                "artifact.xml");
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

    /**
     * {@inheritDoc}
     *
     * <p>Only the read path goes through {@link LocalFileStore}. Generation does not: this service
     * returns a path and the <em>project worker</em> writes the bytes to it, under
     * {@code <root>/<projectId>/<batchId>.mspdi.xml} rather than the {@code <root>/<uuid>/<name>}
     * layout {@code store} produces. That difference does not matter here, because reading only
     * requires the file to sit inside the root and to be a regular file — both layouts do. Routing
     * generation through {@code store} as well would take those bytes away from the worker that
     * owns them.
     */
    @Override
    public InputStream read(String storageUri) throws IOException {
        return fileStore.read(storageUri);
    }
}
