package com.shutdowntracker.api.exportpreview.storage;

import java.util.UUID;

public interface ExportArtifactStorage {

    ExportArtifactStorageLocation prepareExportArtifact(UUID projectId, UUID exportBatchId);
}
