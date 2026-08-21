package com.shutdowntracker.api.exportpreview.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalExportArtifactStorageTests {

    @TempDir
    private Path tempDir;

    @Test
    void readsBackBytesTheWorkerWroteAtThePreparedPath() throws Exception {
        ExportArtifactStorage storage = new LocalExportArtifactStorage(new ExportArtifactStorageProperties(tempDir));
        ExportArtifactStorageLocation location =
                storage.prepareExportArtifact(UUID.randomUUID(), UUID.randomUUID());
        // The worker writes these bytes, not this service, and it writes them under
        // <root>/<projectId>/<batchId>.mspdi.xml rather than the layout LocalFileStore.store
        // produces. Reading only requires the file to be inside the root, so both layouts work --
        // and this is the test that says so.
        Files.writeString(location.outputPath(), "<Project/>");

        try (InputStream content = storage.read(location.storageUri())) {
            assertThat(new String(content.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("<Project/>");
        }
    }

    @Test
    void refusesALocationOutsideTheConfiguredRoot(@TempDir Path elsewhere) throws Exception {
        ExportArtifactStorage storage = new LocalExportArtifactStorage(new ExportArtifactStorageProperties(tempDir));
        Path outside = elsewhere.resolve("someone-elses.xml");
        Files.writeString(outside, "<Project/>");

        assertThatThrownBy(() -> storage.read(outside.toUri().toString()))
                .describedAs("the URI comes from a database column; a row must not name a file this store never wrote")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("outside the configured local root");
    }

    @Test
    void refusesAUriThatIsNotALocalFile() {
        ExportArtifactStorage storage = new LocalExportArtifactStorage(new ExportArtifactStorageProperties(tempDir));

        assertThatThrownBy(() -> storage.read("https://example.test/artifact.xml"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not a local file URI");
    }

    @Test
    void reportsAMissingFileRatherThanReturningNothing() {
        ExportArtifactStorage storage = new LocalExportArtifactStorage(new ExportArtifactStorageProperties(tempDir));
        ExportArtifactStorageLocation location =
                storage.prepareExportArtifact(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> storage.read(location.storageUri()))
                .describedAs("an empty stream would look like an empty schedule")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("does not hold a file");
    }

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
