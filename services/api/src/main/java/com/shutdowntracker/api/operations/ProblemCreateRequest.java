package com.shutdowntracker.api.operations;

import java.util.Objects;
import java.util.UUID;

/** Raising a problem. The raiser is taken from the authenticated request, not the body. */
public record ProblemCreateRequest(
        UUID importedTaskId,
        String title,
        String description,
        ProblemSeverity severity,
        boolean blocksExecution
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
    }
}
