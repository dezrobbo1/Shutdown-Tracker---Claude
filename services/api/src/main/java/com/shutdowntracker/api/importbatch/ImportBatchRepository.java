package com.shutdowntracker.api.importbatch;

import java.util.Optional;
import java.util.UUID;

public interface ImportBatchRepository {

    Optional<ImportBatchRecord> findByProjectIdAndId(UUID projectId, UUID importBatchId);

    ImportBatchRecord create(ImportBatchCreateRequest request);

    ImportBatchRecord updateStatus(UUID importBatchId, ImportBatchStatus status);

    ImportBatchRecord recordParseSummary(ImportBatchParseSummaryUpdate update);

    /**
     * Moves the batch to {@code failed} and records why, so a failed parse stays visible instead of
     * silently reverting to its previous status.
     */
    ImportBatchRecord recordParseFailure(UUID importBatchId, String failureReason);
}
