package com.shutdowntracker.api.sourcefile.storage;

public record StoredSourceFile(
        String storageUri,
        String originalFilename,
        String storedFilename,
        long sizeBytes,
        String contentHashSha256
) {
}
