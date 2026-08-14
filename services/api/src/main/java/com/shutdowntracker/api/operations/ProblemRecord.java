package com.shutdowntracker.api.operations;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProblemRecord(
        UUID id,
        UUID projectId,
        UUID importedTaskId,
        String title,
        String description,
        ProblemStatus status,
        ProblemSeverity severity,
        boolean blocksExecution,
        UUID raisedByUserId,
        UUID assignedToUserId,
        OffsetDateTime resolvedAt,
        UUID resolvedByUserId
) {
}
