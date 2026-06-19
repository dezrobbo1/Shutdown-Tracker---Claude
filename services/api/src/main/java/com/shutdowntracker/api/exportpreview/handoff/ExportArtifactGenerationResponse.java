package com.shutdowntracker.api.exportpreview.handoff;

import com.shutdowntracker.api.exportpreview.ExportPreviewDetail;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationResponse;

public record ExportArtifactGenerationResponse(
        ExportPreviewDetail exportPreview,
        ProjectExportArtifactGenerationResponse workerResponse,
        String message
) {
}
