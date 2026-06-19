package com.shutdowntracker.api.importbatch;

import java.util.Objects;
import java.util.UUID;

public record ImportBatchCreateRequest(
        UUID projectId,
        UUID sourceFileId
) {

    public ImportBatchCreateRequest {
        Objects.requireNonNull(projectId, "projectId is required.");
        Objects.requireNonNull(sourceFileId, "sourceFileId is required.");
    }
}
