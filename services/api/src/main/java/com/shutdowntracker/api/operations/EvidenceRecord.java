package com.shutdowntracker.api.operations;

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
        EvidenceStatus status,
        UUID capturedByUserId,
        String caption
) {
}
