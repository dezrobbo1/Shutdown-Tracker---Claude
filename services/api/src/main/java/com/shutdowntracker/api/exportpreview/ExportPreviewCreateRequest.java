package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableNonEmptyList;
import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.immutableObjectMap;
import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireNonNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ExportPreviewCreateRequest(
        UUID projectSnapshotId,
        List<UUID> candidateIds,
        Map<String, Object> metadata
) {
    public ExportPreviewCreateRequest {
        requireNonNull(projectSnapshotId, "projectSnapshotId is required.");
        if (candidateIds == null || candidateIds.isEmpty()) {
            throw new IllegalArgumentException("At least one authoritative export candidate is required.");
        }
        requireUniqueCandidates(candidateIds);
        candidateIds = immutableNonEmptyList(candidateIds, "At least one authoritative export candidate is required.");
        metadata = immutableObjectMap(metadata, "metadata");
    }

    private static void requireUniqueCandidates(List<UUID> candidateIds) {
        Set<UUID> candidates = new HashSet<>();
        for (UUID candidateId : candidateIds) {
            requireNonNull(candidateId, "candidateIds must not contain null values.");
            if (!candidates.add(candidateId)) {
                throw new IllegalArgumentException("Duplicate authoritative export candidate '" + candidateId + "'.");
            }
        }
    }
}
