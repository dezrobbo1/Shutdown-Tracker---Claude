package com.shutdowntracker.api.importbatch.handoff;

import com.shutdowntracker.api.importbatch.ImportBatchRecord;
import com.shutdowntracker.api.importbatch.ImportBatchService;
import com.shutdowntracker.api.importbatch.ImportBatchStatus;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataRecord;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataService;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ImportBatchParseHandoffService {

    private final ImportBatchService importBatchService;
    private final SourceFileMetadataService sourceFileMetadataService;
    private final ProjectParseHandoffService projectParseHandoffService;

    public ImportBatchParseHandoffService(
            ImportBatchService importBatchService,
            SourceFileMetadataService sourceFileMetadataService,
            ProjectParseHandoffService projectParseHandoffService
    ) {
        this.importBatchService = importBatchService;
        this.sourceFileMetadataService = sourceFileMetadataService;
        this.projectParseHandoffService = projectParseHandoffService;
    }

    /**
     * Deliberately not {@code @Transactional}.
     *
     * <p>This method calls the project worker over HTTP. Holding a database transaction across that call
     * would pin a connection for the life of a remote request and roll the {@code parsing} transition back
     * on failure, hiding the attempt. Each persistence step below commits on its own, so {@code parsing} is
     * visible while the worker runs.
     */
    public ImportBatchParseHandoffResponse requestParseSummary(UUID projectId, UUID importBatchId) {
        Objects.requireNonNull(projectId, "projectId is required.");
        Objects.requireNonNull(importBatchId, "importBatchId is required.");

        ImportBatchRecord importBatch = importBatchService.find(projectId, importBatchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import batch not found."));
        if (importBatch.status() != ImportBatchStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending import batches can request parsing.");
        }

        SourceFileMetadataRecord sourceFile = sourceFileMetadataService.find(projectId, importBatch.sourceFileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source file metadata not found."));

        ImportBatchRecord parsingBatch = importBatchService.updateStatus(importBatchId, ImportBatchStatus.PARSING);
        ProjectParseSummaryResponse parseSummary = projectParseHandoffService.requestParseSummary(parsingBatch, sourceFile);
        if (!parseSummary.importBatchId().equals(importBatchId)) {
            throw new IllegalStateException("Worker parse response referenced a different import batch.");
        }

        ImportBatchRecord parsedBatch = importBatchService.recordParsedSummary(parseSummary);
        return ImportBatchParseHandoffResponse.recorded(parsedBatch, parseSummary);
    }
}
