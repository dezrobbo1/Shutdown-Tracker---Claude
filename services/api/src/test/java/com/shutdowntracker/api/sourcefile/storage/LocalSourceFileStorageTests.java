package com.shutdowntracker.api.sourcefile.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalSourceFileStorageTests {

    @TempDir
    private Path tempDir;

    @Test
    void storesSyntheticBytesAndReturnsStorageMetadata() throws Exception {
        byte[] content = "synthetic".getBytes(StandardCharsets.UTF_8);
        SourceFileStorage storage = new LocalSourceFileStorage(new SourceFileStorageProperties(tempDir));

        StoredSourceFile stored = storage.store(new SourceFileStorageRequest(
                "synthetic-basic-wbs.mspdi.xml",
                new ByteArrayInputStream(content),
                content.length
        ));

        Path storedPath = Path.of(URI.create(stored.storageUri()));

        assertThat(storedPath).startsWith(tempDir.toAbsolutePath());
        assertThat(Files.readAllBytes(storedPath)).isEqualTo(content);
        assertThat(stored.originalFilename()).isEqualTo("synthetic-basic-wbs.mspdi.xml");
        assertThat(stored.storedFilename()).isEqualTo("synthetic-basic-wbs.mspdi.xml");
        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(stored.contentHashSha256()).hasSize(64);
    }

    @Test
    void sanitizesPathLikeFilenamesInsideConfiguredRoot() throws Exception {
        byte[] content = "synthetic".getBytes(StandardCharsets.UTF_8);
        SourceFileStorage storage = new LocalSourceFileStorage(new SourceFileStorageProperties(tempDir));

        StoredSourceFile stored = storage.store(new SourceFileStorageRequest(
                "..\\real-site\\unsafe file.mpp",
                new ByteArrayInputStream(content),
                content.length
        ));

        Path storedPath = Path.of(URI.create(stored.storageUri()));

        assertThat(storedPath).startsWith(tempDir.toAbsolutePath());
        assertThat(stored.storedFilename()).isEqualTo("unsafe_file.mpp");
        assertThat(Files.readAllBytes(storedPath)).isEqualTo(content);
    }

    @Test
    void rejectsInvalidStorageRequests() {
        assertThatThrownBy(() -> new SourceFileStorageRequest(
                "",
                new ByteArrayInputStream("synthetic".getBytes(StandardCharsets.UTF_8)),
                9
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("originalFilename is required.");

        assertThatThrownBy(() -> new SourceFileStorageRequest(
                "example.mpp",
                new ByteArrayInputStream(new byte[0]),
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sizeBytes must be positive.");
    }

    @Test
    void deletesPartialFileWhenExpectedSizeDoesNotMatchWrittenBytes() {
        SourceFileStorage storage = new LocalSourceFileStorage(new SourceFileStorageProperties(tempDir));

        assertThatThrownBy(() -> storage.store(new SourceFileStorageRequest(
                "example.mpp",
                new ByteArrayInputStream("synthetic".getBytes(StandardCharsets.UTF_8)),
                99
        ))).isInstanceOf(Exception.class)
                .hasMessage("Stored byte count did not match requested source file size.");

        assertThat(tempDir).isEmptyDirectory();
    }
}
