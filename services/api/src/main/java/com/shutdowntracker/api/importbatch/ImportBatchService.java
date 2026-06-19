package com.shutdowntracker.api.importbatch;

import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ImportBatchService {

    private final ImportBatchRepository repository;

    public ImportBatchService(ImportBatchRepository repository) {
        this.repository = repository;
    }

    public ImportBatchRecord createPending(UUID projectId, UUID sourceFileId) {
        return repository.create(new ImportBatchCreateRequest(projectId, sourceFileId));
    }

    public ImportBatchRecord updateStatus(UUID importBatchId, ImportBatchStatus status) {
        Objects.requireNonNull(importBatchId, "importBatchId is required.");
        Objects.requireNonNull(status, "status is required.");
        return repository.updateStatus(importBatchId, status);
    }

    public ImportBatchRecord recordParsedSummary(ProjectParseSummaryResponse response) {
        Objects.requireNonNull(response, "response is required.");
        return repository.recordParseSummary(ImportBatchParseSummaryUpdate.from(response));
    }
}
