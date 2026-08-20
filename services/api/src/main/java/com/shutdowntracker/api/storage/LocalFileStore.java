package com.shutdowntracker.api.storage;

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
import java.util.Objects;
import java.util.UUID;

/**
 * Bytes on the local filesystem, confined to one configured root.
 *
 * <p>Two provider-neutral stores are backed by this: evidence binaries, and the candidate
 * schedules a planner returns from Microsoft Project. They keep separate roots, separate
 * interfaces and separate lifecycles, but the part that has to be right — confining a path to its
 * root on the way in and on the way out, hashing what was actually written, and refusing a
 * location this store did not write — is the same code rather than the same code twice.
 *
 * <p>This is a development and review implementation. Production object storage replaces the
 * stores above without their callers changing, which is why nothing here is exposed through them.
 *
 * <p>The labels exist so a failure names what failed. A message reading "storage location is
 * outside the configured local root" tells an operator nothing about which store refused.
 */
public final class LocalFileStore {

    private static final int BUFFER_SIZE = 8192;

    private final Path configuredRoot;
    private final String subject;
    private final String contentNoun;
    private final String fallbackFilename;

    /**
     * @param configuredRoot   the only directory this store will write into or read out of
     * @param subject          how this store names itself in a failure, such as {@code "Evidence storage"}
     * @param contentNoun      what it is storing, such as {@code "uploaded evidence"}
     * @param fallbackFilename the stored filename to use when sanitizing leaves nothing usable
     */
    public LocalFileStore(Path configuredRoot, String subject, String contentNoun, String fallbackFilename) {
        this.configuredRoot = Objects.requireNonNull(configuredRoot, "configuredRoot is required.");
        this.subject = Objects.requireNonNull(subject, "subject is required.");
        this.contentNoun = Objects.requireNonNull(contentNoun, "contentNoun is required.");
        this.fallbackFilename = Objects.requireNonNull(fallbackFilename, "fallbackFilename is required.");
    }

    /**
     * Writes one file under a fresh directory in the root, hashing it as it goes.
     *
     * <p>The hash is computed from the bytes that were written rather than from anything the
     * caller supplied, so it is evidence about the stored file and not a value passed through.
     *
     * <p>A short or over-long stream leaves nothing behind: a partial file that reports the
     * declared size would be indistinguishable from a complete one later.
     */
    public StoredFile store(String originalFilename, InputStream content, long declaredSizeBytes)
            throws IOException {
        Objects.requireNonNull(content, "content is required.");
        Path root = root();
        String storedFilename = sanitizeFilename(originalFilename);
        Path directory = root.resolve(UUID.randomUUID().toString()).normalize();
        Path target = directory.resolve(storedFilename).normalize();

        if (!target.startsWith(root)) {
            throw new IOException(subject + " target escaped the configured local root.");
        }

        Files.createDirectories(directory);

        MessageDigest digest = sha256();
        long bytesWritten = writeAndHash(content, target, digest);
        if (bytesWritten != declaredSizeBytes) {
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
            throw new IOException("Stored byte count did not match the " + contentNoun + " size.");
        }

        return new StoredFile(
                target.toUri().toString(),
                storedFilename,
                bytesWritten,
                HexFormat.of().formatHex(digest.digest()));
    }

    /**
     * Opens a stored file for reading. The caller closes the stream.
     *
     * <p>The URI arrives from a database column. Confining it to the configured root is what stops
     * a row from naming a file this store never wrote.
     */
    public InputStream read(String storageUri) throws IOException {
        Path root = root();
        Path target = resolve(storageUri);

        if (!target.startsWith(root)) {
            throw new IOException(subject + " location is outside the configured local root.");
        }
        if (!Files.isRegularFile(target)) {
            throw new IOException(subject + " location does not hold a file.");
        }
        return Files.newInputStream(target, StandardOpenOption.READ);
    }

    private Path resolve(String storageUri) throws IOException {
        try {
            URI uri = new URI(storageUri);
            if (!"file".equals(uri.getScheme())) {
                throw new IOException(subject + " location is not a local file URI.");
            }
            return Path.of(uri).toAbsolutePath().normalize();
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new IOException(subject + " location is not a readable URI.", exception);
        }
    }

    private Path root() {
        return configuredRoot.toAbsolutePath().normalize();
    }

    private long writeAndHash(InputStream content, Path target, MessageDigest digest) throws IOException {
        long bytesWritten = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (
                InputStream input = content;
                OutputStream output =
                        Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
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
        if (originalFilename == null) {
            return fallbackFilename;
        }

        String normalizedSeparators = originalFilename.replace('\\', '/');
        int separatorIndex = normalizedSeparators.lastIndexOf('/');
        String basename = separatorIndex >= 0
                ? normalizedSeparators.substring(separatorIndex + 1)
                : normalizedSeparators;

        String sanitized = basename
                .replaceAll("[^A-Za-z0-9._-]", "_")
                .replaceAll("^\\.+", "_");

        if (sanitized.isBlank() || sanitized.equals("_")) {
            return fallbackFilename;
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

    /** What a store wrote, described by the bytes that reached the disk. */
    public record StoredFile(
            String storageUri,
            String storedFilename,
            long sizeBytes,
            String contentHashSha256
    ) {
    }
}
