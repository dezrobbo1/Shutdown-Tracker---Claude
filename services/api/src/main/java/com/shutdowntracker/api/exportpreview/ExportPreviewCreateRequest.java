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
        List<ExportPreviewLineCreateRequest> lines,
        Map<String, Object> metadata
) {
    public ExportPreviewCreateRequest {
        requireNonNull(projectSnapshotId, "projectSnapshotId is required.");
        lines = immutableNonEmptyList(lines, "At least one export preview line is required.");
        requireUniqueCandidates(lines);
        metadata = immutableObjectMap(metadata, "metadata");
    }

    private static void requireUniqueCandidates(List<ExportPreviewLineCreateRequest> lines) {
        Set<CandidateKey> candidates = new HashSet<>();
        for (ExportPreviewLineCreateRequest line : lines) {
            CandidateKey candidate = new CandidateKey(line.importedTaskId(), line.fieldName());
            if (!candidates.add(candidate)) {
                throw new IllegalArgumentException(
                        "Duplicate export preview candidate for importedTaskId '"
                                + line.importedTaskId()
                                + "' and fieldName '"
                                + line.fieldName()
                                + "'."
                );
            }
        }
    }

    private record CandidateKey(UUID importedTaskId, String fieldName) {
    }
}
