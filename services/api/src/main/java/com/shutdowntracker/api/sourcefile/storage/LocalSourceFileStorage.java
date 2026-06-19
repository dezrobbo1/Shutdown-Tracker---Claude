package com.shutdowntracker.api.sourcefile.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LocalSourceFileStorage implements SourceFileStorage {

    private static final int BUFFER_SIZE = 8192;

    private final SourceFileStorageProperties properties;

    public LocalSourceFileStorage(SourceFileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredSourceFile store(SourceFileStorageRequest request) throws IOException {
        Path root = properties.localRoot().toAbsolutePath().normalize();
        String storedFilename = sanitizeFilename(request.originalFilename());
        Path directory = root.resolve(UUID.randomUUID().toString()).normalize();
        Path target = directory.resolve(storedFilename).normalize();

        if (!target.startsWith(root)) {
            throw new IOException("Source file storage target escaped the configured local root.");
        }

        Files.createDirectories(directory);

        MessageDigest digest = sha256();
        long bytesWritten = writeAndHash(request, target, digest);
        if (bytesWritten != request.sizeBytes()) {
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
            throw new IOException("Stored byte count did not match requested source file size.");
        }

        return new StoredSourceFile(
                target.toUri().toString(),
                request.originalFilename(),
                storedFilename,
                bytesWritten,
                HexFormat.of().formatHex(digest.digest())
        );
    }

    private long writeAndHash(SourceFileStorageRequest request, Path target, MessageDigest digest) throws IOException {
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
            return "source-file";
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
