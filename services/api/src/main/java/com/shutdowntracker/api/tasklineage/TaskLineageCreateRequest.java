package com.shutdowntracker.api.tasklineage;

import static com.shutdowntracker.api.tasklineage.TaskLineageRecordValidation.immutableObjectMap;
import static com.shutdowntracker.api.tasklineage.TaskLineageRecordValidation.requireConfidence;
import static com.shutdowntracker.api.tasklineage.TaskLineageRecordValidation.requireNonNull;
import static com.shutdowntracker.api.tasklineage.TaskLineageRecordValidation.requireText;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record TaskLineageCreateRequest(
        UUID previousSnapshotId,
        UUID currentSnapshotId,
        UUID previousImportedTaskId,
        UUID currentImportedTaskId,
        String matchMethod,
        BigDecimal matchConfidence,
        Map<String, Object> metadata
) {
    public TaskLineageCreateRequest {
        requireNonNull(previousSnapshotId, "previousSnapshotId is required.");
        requireNonNull(currentSnapshotId, "currentSnapshotId is required.");
        requireNonNull(previousImportedTaskId, "previousImportedTaskId is required.");
        requireNonNull(currentImportedTaskId, "currentImportedTaskId is required.");
        if (previousSnapshotId.equals(currentSnapshotId)) {
            throw new IllegalArgumentException("previousSnapshotId and currentSnapshotId must be different.");
        }
        requireText(matchMethod, "matchMethod is required.");
        requireConfidence(matchConfidence, "matchConfidence");
        metadata = immutableObjectMap(metadata, "metadata");
    }
}
