package com.shutdowntracker.api.exportpreview.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalExportArtifactStorageTests {

    @TempDir
    private Path tempDir;

    @Test
    void preparesSyntheticExportArtifactLocationInsideConfiguredRoot() {
        UUID projectId = UUID.randomUUID();
        UUID exportBatchId = UUID.randomUUID();
        ExportArtifactStorage storage = new LocalExportArtifactStorage(new ExportArtifactStorageProperties(tempDir));

        ExportArtifactStorageLocation location = storage.prepareExportArtifact(projectId, exportBatchId);

        assertThat(location.projectId()).isEqualTo(projectId);
        assertThat(location.exportBatchId()).isEqualTo(exportBatchId);
        assertThat(location.artifactFilename()).isEqualTo(exportBatchId + ".mspdi.xml");
        assertThat(location.outputPath().toAbsolutePath().normalize().startsWith(tempDir.toAbsolutePath().normalize()))
                .isTrue();
        assertThat(location.outputPath().toString()).endsWith(exportBatchId + ".mspdi.xml");
        assertThat(location.storageUri()).isEqualTo(location.outputPath().toUri().toString());
        assertThat(location.storageKind()).isEqualTo("local_filesystem");
        assertThat(Files.isDirectory(location.outputPath().getParent())).isTrue();
        assertThat(location.outputPath()).doesNotExist();
    }

    @Test
    void rejectsMissingStorageIdentifiers() {
        ExportArtifactStorage storage = new LocalExportArtifactStorage(new ExportArtifactStorageProperties(tempDir));

        assertThatThrownBy(() -> storage.prepareExportArtifact(null, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("projectId is required.");

        assertThatThrownBy(() -> storage.prepareExportArtifact(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exportBatchId is required.");
    }

    @Test
    void rejectsNonXmlArtifactLocationRecords() {
        assertThatThrownBy(() -> new ExportArtifactStorageLocation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "export.zip",
                tempDir.resolve("export.zip"),
                tempDir.resolve("export.zip").toUri().toString(),
                "local_filesystem"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("artifactFilename must end with .xml.");
    }
}
