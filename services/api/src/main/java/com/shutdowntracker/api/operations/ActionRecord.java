package com.shutdowntracker.api.operations;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ActionRecord(
        UUID id,
        UUID projectId,
        UUID problemId,
        UUID importedTaskId,
        String title,
        String description,
        ActionStatus status,
        UUID assignedToUserId,
        OffsetDateTime dueAt,
        UUID createdByUserId,
        OffsetDateTime completedAt,
        UUID completedByUserId
) {
}
