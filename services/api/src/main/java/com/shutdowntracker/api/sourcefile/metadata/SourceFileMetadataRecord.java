package com.shutdowntracker.api.sourcefile.metadata;

import java.util.UUID;

public record SourceFileMetadataRecord(
        UUID id,
        UUID projectId,
        String originalFilename,
        SourceFileKind fileKind,
        String storageUri,
        String contentHash,
        long sizeBytes
) {
}
