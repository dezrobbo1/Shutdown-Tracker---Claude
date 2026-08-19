package com.shutdowntracker.api.operations;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EvidenceRecord(
        UUID id,
        UUID projectId,
        UUID importedTaskId,
        UUID problemId,
        UUID actionId,
        UUID taskProgressUpdateId,
        String originalFilename,
        String contentType,
        String storageUri,
        Long sizeBytes,
        String contentHash,
        EvidenceStatus status,
        UUID capturedByUserId,
        OffsetDateTime capturedAt,
        String caption
) {
}
