package com.shutdowntracker.api.importbatch;

import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import com.shutdowntracker.api.actor.Actor;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ImportBatchService {

    private final ImportBatchRepository repository;

    public ImportBatchService(ImportBatchRepository repository) {
        this.repository = repository;
    }

    public Optional<ImportBatchRecord> find(UUID projectId, UUID importBatchId) {
        Objects.requireNonNull(projectId, "projectId is required.");
        Objects.requireNonNull(importBatchId, "importBatchId is required.");
        return repository.findByProjectIdAndId(projectId, importBatchId);
    }

    public ImportBatchRecord createPending(UUID projectId, Actor actor, UUID sourceFileId) {
        Objects.requireNonNull(actor, "actor is required.");
        return repository.create(new ImportBatchCreateRequest(projectId, sourceFileId, actor.userId()));
    }

    @Transactional
    public ImportBatchRecord updateStatus(UUID importBatchId, ImportBatchStatus status) {
        Objects.requireNonNull(importBatchId, "importBatchId is required.");
        Objects.requireNonNull(status, "status is required.");
        return repository.updateStatus(importBatchId, status);
    }

    @Transactional
    public ImportBatchRecord recordParsedSummary(ProjectParseSummaryResponse response) {
        Objects.requireNonNull(response, "response is required.");
        return repository.recordParseSummary(ImportBatchParseSummaryUpdate.from(response));
    }

    @Transactional
    public ImportBatchRecord recordParseFailure(UUID importBatchId, String failureReason) {
        Objects.requireNonNull(importBatchId, "importBatchId is required.");
        return repository.recordParseFailure(importBatchId, failureReason);
    }
}
