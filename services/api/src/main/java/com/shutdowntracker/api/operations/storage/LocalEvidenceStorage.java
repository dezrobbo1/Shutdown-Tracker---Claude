package com.shutdowntracker.api.operations.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Evidence binaries on the local filesystem, for development and review.
 *
 * <p>Production object storage is a separate open item. This implementation exists so the rest of
 * the evidence path — upload, status, audit, read-back — can be built and proved against a real
 * store rather than waiting on one.
 */
@Service
public class LocalEvidenceStorage implements EvidenceStorage {

    private static final int BUFFER_SIZE = 8192;

    private final EvidenceStorageProperties properties;

    public LocalEvidenceStorage(EvidenceStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredEvidence store(EvidenceStorageRequest request) throws IOException {
        Path root = root();
        String storedFilename = sanitizeFilename(request.originalFilename());
        Path directory = root.resolve(UUID.randomUUID().toString()).normalize();
        Path target = directory.resolve(storedFilename).normalize();

        if (!target.startsWith(root)) {
            throw new IOException("Evidence storage target escaped the configured local root.");
        }

        Files.createDirectories(directory);

        MessageDigest digest = sha256();
        long bytesWritten = writeAndHash(request, target, digest);
        if (bytesWritten != request.sizeBytes()) {
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
            throw new IOException("Stored byte count did not match the uploaded evidence size.");
        }

        return new StoredEvidence(
                target.toUri().toString(),
                request.originalFilename(),
                storedFilename,
                bytesWritten,
                HexFormat.of().formatHex(digest.digest())
        );
    }

    @Override
    public InputStream read(String storageUri) throws IOException {
        Path root = root();
        Path target = resolve(storageUri);

        // The URI arrives from a database column. Confining it to the configured root is what
        // stops a row from naming a file this store never wrote.
        if (!target.startsWith(root)) {
            throw new IOException("Evidence storage location is outside the configured local root.");
        }
        if (!Files.isRegularFile(target)) {
            throw new IOException("Evidence storage location does not hold a file.");
        }
        return Files.newInputStream(target, StandardOpenOption.READ);
    }

    private Path resolve(String storageUri) throws IOException {
        try {
            URI uri = new URI(storageUri);
            if (!"file".equals(uri.getScheme())) {
                throw new IOException("Evidence storage location is not a local file URI.");
            }
            return Path.of(uri).toAbsolutePath().normalize();
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new IOException("Evidence storage location is not a readable URI.", exception);
        }
    }

    private Path root() {
        return properties.localRoot().toAbsolutePath().normalize();
    }

    private long writeAndHash(EvidenceStorageRequest request, Path target, MessageDigest digest) throws IOException {
        long bytesWritten = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (
                InputStream input = request.content();
                OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        ) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
                bytesWritten += read;
            }
        }

        return bytesWritten;
    }

    private String sanitizeFilename(String originalFilename) {
        String normalizedSeparators = originalFilename.replace('\\', '/');
        int separatorIndex = normalizedSeparators.lastIndexOf('/');
        String basename = separatorIndex >= 0
                ? normalizedSeparators.substring(separatorIndex + 1)
                : normalizedSeparators;

        String sanitized = basename
                .replaceAll("[^A-Za-z0-9._-]", "_")
                .replaceAll("^\\.+", "_");

        if (sanitized.isBlank() || sanitized.equals("_")) {
            return "evidence";
        }
        return sanitized;
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available.", exception);
        }
    }
}
