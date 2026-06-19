package com.shutdowntracker.api.sourcefile.metadata;

import java.util.Objects;
import java.util.UUID;

public record SourceFileMetadataCreateRequest(
        UUID projectId,
        String originalFilename,
        SourceFileKind fileKind,
        String storageUri,
        String contentHash,
        long sizeBytes
) {

    public SourceFileMetadataCreateRequest {
        Objects.requireNonNull(projectId, "projectId is required.");
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
