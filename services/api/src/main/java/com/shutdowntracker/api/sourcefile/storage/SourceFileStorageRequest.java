package com.shutdowntracker.api.sourcefile.storage;

import java.io.InputStream;
import java.util.Objects;

public record SourceFileStorageRequest(
        String originalFilename,
        InputStream content,
        long sizeBytes
) {

    public SourceFileStorageRequest {
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
