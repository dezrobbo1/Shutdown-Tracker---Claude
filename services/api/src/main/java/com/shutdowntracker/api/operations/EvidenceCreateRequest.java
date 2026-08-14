package com.shutdowntracker.api.operations;

import java.util.Objects;
import java.util.UUID;

/**
 * Registers evidence metadata. Binary content is not carried here; it goes to object
 * storage and {@code storageUri} points at it.
 */
public record EvidenceCreateRequest(
        UUID importedTaskId,
        UUID problemId,
        UUID actionId,
        UUID taskProgressUpdateId,
        String originalFilename,
        String contentType,
        String storageUri,
        Long sizeBytes,
        String caption
) {
    public EvidenceCreateRequest {
        Objects.requireNonNull(originalFilename, "originalFilename is required.");
        if (originalFilename.isBlank()) {
            throw new IllegalArgumentException("originalFilename is required.");
        }
        originalFilename = originalFilename.trim();

        if (importedTaskId == null && problemId == null && actionId == null && taskProgressUpdateId == null) {
            throw new IllegalArgumentException("Evidence must reference a task, problem, action, or progress update.");
        }
        if (sizeBytes != null && sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must not be negative.");
        }
        if (storageUri != null && storageUri.isBlank()) {
            storageUri = null;
        }
        if (caption != null && caption.isBlank()) {
            caption = null;
        }
    }
}
