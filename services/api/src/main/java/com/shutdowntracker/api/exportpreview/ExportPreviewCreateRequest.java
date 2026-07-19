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
        Set<UUID> candidates = new HashSet<>();
        for (ExportPreviewLineCreateRequest line : lines) {
            if (!candidates.add(line.authoritativeExportCandidateId())) {
                throw new IllegalArgumentException(
                        "Duplicate authoritative export candidate '"
                                + line.authoritativeExportCandidateId()
                                + "'."
                );
            }
        }
    }
}
