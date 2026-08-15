package com.shutdowntracker.projectworker.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Confines caller-supplied storage paths to configured local roots. */
@Service
public class WorkerStoragePathResolver {

    private static final String LOCAL_FILE_SCHEME = "file";
    private final WorkerStorageProperties properties;

    public WorkerStoragePathResolver(WorkerStorageProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties is required.");
    }

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
        try {
            Path direct = Path.of(value);
            if (direct.isAbsolute()) {
                return direct.toAbsolutePath().normalize();
            }
        } catch (InvalidPathException ignored) {
            // Try URI parsing below.
        }
        URI uri = URI.create(value);
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
