package com.shutdowntracker.api.exportpreview;

import static com.shutdowntracker.api.exportpreview.ExportPreviewRecordValidation.requireNonNull;

import java.util.UUID;

public record ExportPreviewLineCreateRequest(
        UUID authoritativeExportCandidateId
) {
    public ExportPreviewLineCreateRequest {
        requireNonNull(authoritativeExportCandidateId, "authoritativeExportCandidateId is required.");
    }
}
