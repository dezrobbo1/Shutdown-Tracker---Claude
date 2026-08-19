package com.shutdowntracker.api.operations.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalEvidenceStorageTests {

    @TempDir
    private Path tempDir;

    @TempDir
    private Path outsideRoot;

    @Test
    void storesBytesInsideTheConfiguredRootAndReadsThemBack() throws Exception {
        byte[] content = "blanking plate fitted".getBytes(StandardCharsets.UTF_8);
        EvidenceStorage storage = storage();

        StoredEvidence stored = storage.store(new EvidenceStorageRequest(
                "blanking-plate.jpg", new ByteArrayInputStream(content), content.length));

        assertThat(Path.of(URI.create(stored.storageUri()))).startsWith(tempDir.toAbsolutePath());
        assertThat(stored.storedFilename()).isEqualTo("blanking-plate.jpg");
        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(stored.contentHashSha256()).hasSize(64);

        try (InputStream read = storage.read(stored.storageUri())) {
            assertThat(read.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void sanitizesPathLikeFilenamesInsideTheConfiguredRoot() throws Exception {
        byte[] content = "photo".getBytes(StandardCharsets.UTF_8);
        EvidenceStorage storage = storage();

        StoredEvidence stored = storage.store(new EvidenceStorageRequest(
                "..\\real-site\\permit photo.jpg", new ByteArrayInputStream(content), content.length));

        assertThat(Path.of(URI.create(stored.storageUri()))).startsWith(tempDir.toAbsolutePath());
        assertThat(stored.storedFilename()).isEqualTo("permit_photo.jpg");
    }

    /**
     * The URI handed to {@code read} comes from a database column. A store that fetches whatever it
     * is given turns an evidence row into a way to read any file the process can reach.
     */
    @Test
    void refusesToReadAFileOutsideTheConfiguredRoot() throws Exception {
        Path outside = outsideRoot.resolve("payroll.csv");
        Files.writeString(outside, "not evidence");

        assertThatThrownBy(() -> storage().read(outside.toUri().toString()))
                .hasMessageContaining("outside the configured local root");
    }

    @Test
    void refusesToReadAStorageLocationThatIsNotALocalFileUri() {
        assertThatThrownBy(() -> storage().read("s3://evidence/blanking-plate.jpg"))
                .hasMessageContaining("not a local file URI");
    }

    @Test
    void reportsAMissingFileRatherThanReturningAnEmptyStream() {
        assertThatThrownBy(() -> storage().read(tempDir.resolve("never-written.jpg").toUri().toString()))
                .hasMessageContaining("does not hold a file");
    }

    @Test
    void deletesThePartialFileWhenTheWrittenBytesDoNotMatchTheDeclaredSize() {
        assertThatThrownBy(() -> storage().store(new EvidenceStorageRequest(
                "short.jpg", new ByteArrayInputStream("short".getBytes(StandardCharsets.UTF_8)), 99)))
                .hasMessage("Stored byte count did not match the uploaded evidence size.");

        assertThat(tempDir).isEmptyDirectory();
    }

    private EvidenceStorage storage() {
        return new LocalEvidenceStorage(new EvidenceStorageProperties(tempDir, 1_048_576L));
    }
}
