package com.shutdowntracker.projectworker.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Resolves caller-supplied storage locations to local paths that are confined to a configured root.
 *
 * <p>Worker handoff requests carry a storage URI or output path chosen by the API. The worker still treats
 * those values as untrusted input: a request must never make the worker read or write outside its configured
 * source-file and export-artifact roots.
 */
@Service
public class WorkerStoragePathResolver {

    private static final String LOCAL_FILE_SCHEME = "file";

    private final WorkerStorageProperties properties;

    public WorkerStoragePathResolver(WorkerStorageProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties is required.");
    }

    /**
     * Resolves an existing source file for parsing, confined to the configured source-file root.
     */
    public Path resolveSourceFile(String storageUri) {
        Path candidate = toLocalPath(storageUri, "storageUri");
        Path root = realRoot(properties.sourceFileRoot());

        if (!Files.isRegularFile(candidate)) {
            throw new IllegalArgumentException("Project worker source file was not found: " + candidate.getFileName());
        }

        Path resolved = toRealPath(candidate);
        confine(resolved, root, "source file");
        return resolved;
    }

    /**
     * Resolves an export artifact output path, confined to the configured export-artifact root.
     *
     * <p>The file itself does not exist yet, so the deepest existing ancestor is resolved through symlinks
     * and checked instead. This prevents a symlinked parent directory from redirecting the write.
     */
    public Path resolveExportArtifactOutput(String outputPath) {
        Path candidate = toLocalPath(outputPath, "outputPath");
        Path root = realRoot(properties.exportArtifactRoot());

        confine(candidate, root, "export artifact");
        confine(toRealPath(deepestExistingAncestor(candidate)), root, "export artifact");
        return candidate;
    }

    private Path toLocalPath(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException ignored) {
            return Path.of(value).toAbsolutePath().normalize();
        }

        if (uri.getScheme() == null) {
            return Path.of(value).toAbsolutePath().normalize();
        }
        if (LOCAL_FILE_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return Path.of(uri).toAbsolutePath().normalize();
        }
        throw new IllegalArgumentException("Project worker handoff only supports local file storage URIs for now.");
    }

    private void confine(Path candidate, Path root, String description) {
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException(
                    "Project worker " + description + " path resolved outside the configured storage root."
            );
        }
    }

    private Path realRoot(Path configuredRoot) {
        Path absoluteRoot = configuredRoot.toAbsolutePath().normalize();
        return Files.exists(absoluteRoot) ? toRealPath(absoluteRoot) : absoluteRoot;
    }

    private Path deepestExistingAncestor(Path path) {
        Path current = path;
        while (current != null && !Files.exists(current)) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalArgumentException("Project worker storage path has no existing parent directory.");
        }
        return current;
    }

    private Path toRealPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to resolve project worker storage path.", exception);
        }
    }
}
