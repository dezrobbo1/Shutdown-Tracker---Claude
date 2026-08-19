package com.shutdowntracker.api.operations.storage;

import java.io.InputStream;
import java.util.Objects;

public record EvidenceStorageRequest(
        String originalFilename,
        InputStream content,
        long sizeBytes
) {

    public EvidenceStorageRequest {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("originalFilename is required.");
        }
        Objects.requireNonNull(content, "content is required.");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive.");
        }
        originalFilename = originalFilename.trim();
    }
}
