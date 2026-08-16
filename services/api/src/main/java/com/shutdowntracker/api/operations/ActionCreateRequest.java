package com.shutdowntracker.api.operations;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public record ActionCreateRequest(
        UUID problemId,
        UUID importedTaskId,
        String title,
        String description,
        UUID assignedToUserId,
        OffsetDateTime dueAt
) {
    public ActionCreateRequest {
        Objects.requireNonNull(title, "title is required.");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title is required.");
        }
        title = title.trim();
        if (description != null && description.isBlank()) {
            description = null;
        }
    }
}
