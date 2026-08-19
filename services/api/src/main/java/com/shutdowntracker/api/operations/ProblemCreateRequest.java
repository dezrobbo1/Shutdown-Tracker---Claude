package com.shutdowntracker.api.operations;

import java.util.Objects;
import java.util.UUID;

/**
 * Raising a problem. The raiser is taken from the authenticated request, not the body.
 *
 * <p>{@code idempotencyKey} lets a problem captured offline be retried without raising it
 * twice; {@code offlineLocalId} records which device capture became this problem. Both are
 * absent when a problem is raised from a connected console.
 */
public record ProblemCreateRequest(
        UUID importedTaskId,
        String title,
        String description,
        ProblemSeverity severity,
        boolean blocksExecution,
        String idempotencyKey,
        String offlineLocalId
) {
    public ProblemCreateRequest {
        Objects.requireNonNull(title, "title is required.");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title is required.");
        }
        title = title.trim();
        severity = severity == null ? ProblemSeverity.MEDIUM : severity;
        if (description != null && description.isBlank()) {
            description = null;
        }
        // A blank key is not a key. Treating one as present would make the partial unique
        // index reject every later capture that also sent nothing.
        if (idempotencyKey != null && idempotencyKey.isBlank()) {
            idempotencyKey = null;
        }
        if (offlineLocalId != null && offlineLocalId.isBlank()) {
            offlineLocalId = null;
        }
    }

    /** A problem raised from a connected client, with no offline capture behind it. */
    public ProblemCreateRequest(
            UUID importedTaskId,
            String title,
            String description,
            ProblemSeverity severity,
            boolean blocksExecution
    ) {
        this(importedTaskId, title, description, severity, blocksExecution, null, null);
    }
}
