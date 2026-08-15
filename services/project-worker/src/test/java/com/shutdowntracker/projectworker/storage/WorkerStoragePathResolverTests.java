package com.shutdowntracker.projectworker.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkerStoragePathResolverTests {

    @TempDir
    private Path tempDir;

    @Test
    void resolvesExistingSourceInsideConfiguredRoot() throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("sources"));
        Path artifactRoot = Files.createDirectories(tempDir.resolve("artifacts"));
        Path source = Files.writeString(sourceRoot.resolve("synthetic.mspdi.xml"), "<Project/>");
        WorkerStoragePathResolver resolver = new WorkerStoragePathResolver(
                new WorkerStorageProperties(sourceRoot, artifactRoot)
        );

        assertThat(resolver.resolveSourceFile(source.toUri().toString())).isEqualTo(source.toRealPath());
    }

    @Test
    void rejectsSourceOutsideConfiguredRoot() throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("sources"));
        Path artifactRoot = Files.createDirectories(tempDir.resolve("artifacts"));
        Path outside = Files.writeString(tempDir.resolve("outside.xml"), "<Project/>");
        WorkerStoragePathResolver resolver = new WorkerStoragePathResolver(
                new WorkerStorageProperties(sourceRoot, artifactRoot)
        );

        assertThatThrownBy(() -> resolver.resolveSourceFile(outside.toUri().toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the configured storage root");
    }

    @Test
    void rejectsExportTraversalOutsideConfiguredRoot() throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("sources"));
        Path artifactRoot = Files.createDirectories(tempDir.resolve("artifacts"));
        WorkerStoragePathResolver resolver = new WorkerStoragePathResolver(
                new WorkerStorageProperties(sourceRoot, artifactRoot)
        );

        assertThatThrownBy(() -> resolver.resolveExportArtifactOutput(
                artifactRoot.resolve("../escaped.mspdi.xml").toString()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the configured storage root");
    }

    @Test
    void rejectsNonLocalUriSchemes() throws Exception {
        WorkerStoragePathResolver resolver = new WorkerStoragePathResolver(new WorkerStorageProperties(
                Files.createDirectories(tempDir.resolve("sources")),
                Files.createDirectories(tempDir.resolve("artifacts"))
        ));

        assertThatThrownBy(() -> resolver.resolveExportArtifactOutput("s3://bucket/export.mspdi.xml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only supports local file storage URIs");
    }
}
