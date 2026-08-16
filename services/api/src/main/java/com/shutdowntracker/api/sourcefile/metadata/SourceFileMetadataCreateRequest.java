package com.shutdowntracker.api.sourcefile.metadata;

import java.util.Objects;
import java.util.UUID;

/**
 * A stored upload, and who uploaded it.
 *
 * <p>{@code uploadedByUserId} comes from the resolved actor, never from the request: the column is
 * a foreign key to {@code users} and is what the audit trail reads to say who brought a schedule in.
 */
public record SourceFileMetadataCreateRequest(
        UUID projectId,
        UUID uploadedByUserId,
        String originalFilename,
        SourceFileKind fileKind,
        String storageUri,
        String contentHash,
        long sizeBytes
) {

    public SourceFileMetadataCreateRequest {
        Objects.requireNonNull(projectId, "projectId is required.");
        Objects.requireNonNull(uploadedByUserId, "uploadedByUserId is required.");
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("originalFilename is required.");
        }
        Objects.requireNonNull(fileKind, "fileKind is required.");
        if (storageUri == null || storageUri.isBlank()) {
            throw new IllegalArgumentException("storageUri is required.");
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash is required.");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive.");
        }
        originalFilename = originalFilename.trim();
        storageUri = storageUri.trim();
        contentHash = contentHash.trim();
    }
}
