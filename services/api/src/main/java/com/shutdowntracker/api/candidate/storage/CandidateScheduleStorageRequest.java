package com.shutdowntracker.api.candidate.storage;

import java.io.InputStream;
import java.util.Objects;

public record CandidateScheduleStorageRequest(
        String originalFilename,
        InputStream content,
        long sizeBytes
) {

    public CandidateScheduleStorageRequest {
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
