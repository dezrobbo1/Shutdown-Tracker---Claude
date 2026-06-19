package com.shutdowntracker.api.importbatch;

import java.util.UUID;

public interface ImportBatchRepository {

    ImportBatchRecord create(ImportBatchCreateRequest request);

    ImportBatchRecord updateStatus(UUID importBatchId, ImportBatchStatus status);
}
