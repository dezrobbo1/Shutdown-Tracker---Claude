package com.shutdowntracker.api.operations.storage;

public record StoredEvidence(
        String storageUri,
        String originalFilename,
        String storedFilename,
        long sizeBytes,
        String contentHashSha256
) {
}
