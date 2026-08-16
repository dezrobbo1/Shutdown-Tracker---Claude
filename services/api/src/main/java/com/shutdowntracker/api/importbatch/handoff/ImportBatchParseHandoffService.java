package com.shutdowntracker.api.importbatch.handoff;

import com.shutdowntracker.api.audit.AuditEventCategory;
import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventRecorder;
import com.shutdowntracker.api.audit.AuditEventTypes;
import com.shutdowntracker.api.importbatch.ImportBatchRecord;
import com.shutdowntracker.api.importbatch.ImportBatchService;
import com.shutdowntracker.api.importbatch.ImportBatchStatus;
import com.shutdowntracker.api.importedproject.ImportedProjectPersistenceResult;
import com.shutdowntracker.api.importedproject.ImportedProjectPersistenceService;
import com.shutdowntracker.api.importedproject.ImportedProjectSnapshotCreateRequest;
import com.shutdowntracker.api.importedproject.ParsedProjectEntitiesMapper;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataRecord;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataService;
import com.shutdowntracker.projectimport.contract.ProjectParseEntitiesResponse;
import java.util.Map;
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
    private final ImportedProjectPersistenceService persistenceService;
    private final AuditEventRecorder auditEventRecorder;

    public ImportBatchParseHandoffService(
            ImportBatchService importBatchService,
            SourceFileMetadataService sourceFileMetadataService,
            ProjectParseHandoffService projectParseHandoffService,
            ImportedProjectPersistenceService persistenceService,
            AuditEventRecorder auditEventRecorder
    ) {
        this.importBatchService = importBatchService;
        this.sourceFileMetadataService = sourceFileMetadataService;
        this.projectParseHandoffService = projectParseHandoffService;
        this.persistenceService = persistenceService;
        this.auditEventRecorder = auditEventRecorder;
    }

    /**
     * Deliberately not {@code @Transactional}.
     *
     * <p>This method calls the project worker over HTTP. Holding a database transaction across that call
     * would pin a connection for the life of a remote request and roll the {@code parsing} transition back
     * on failure, hiding the attempt. Each persistence step below commits on its own, so {@code parsing} is
     * visible while the worker runs and {@code failed} survives a terminal failure.
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

        ProjectParseEntitiesResponse parsed;
        try {
            parsed = projectParseHandoffService.requestParseEntities(parsingBatch, sourceFile);
            if (!parsed.summary().importBatchId().equals(importBatchId)) {
                throw new IllegalStateException("Worker parse response referenced a different import batch.");
            }
        } catch (RuntimeException exception) {
            // Record the terminal failure so the batch does not sit in parsing with no explanation.
            recordParseFailure(projectId, importBatchId, exception);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Project worker parse request failed.",
                    exception
            );
        }

        // Everything past the worker call is also recorded as a terminal failure when it
        // throws. Without this a snapshot that fails to store would leave the batch in
        // parsing forever, which is the same stuck state the worker-failure path avoids.
        try {
            ImportedProjectPersistenceResult persisted = persistenceService.persistParsedSnapshot(
                    new ImportedProjectSnapshotCreateRequest(
                            projectId,
                            importBatchId,
                            parsed.externalProjectUid(),
                            parsed.summary().projectName(),
                            parsed.projectStatusDate(),
                            Map.of(
                                    "parserName", parsed.summary().parserName(),
                                    "parserVersion", parsed.summary().parserVersion(),
                                    "sourceFilename", parsed.summary().sourceFilename(),
                                    "detectedFormat", parsed.summary().detectedFormat()
                            ),
                            ParsedProjectEntitiesMapper.toEntities(parsed)
                    ));

            ImportBatchRecord parsedBatch = importBatchService.recordParsedSummary(parsed.summary());
            recordSnapshotImported(projectId, importBatchId, persisted);
            return ImportBatchParseHandoffResponse.recorded(parsedBatch, parsed.summary(), persisted);
        } catch (RuntimeException exception) {
            recordParseFailure(projectId, importBatchId, exception);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Parsed project snapshot could not be stored.",
                    exception
            );
        }
    }

    private void recordSnapshotImported(
            UUID projectId,
            UUID importBatchId,
            ImportedProjectPersistenceResult persisted
    ) {
        auditEventRecorder.record(AuditEventCreateRequest.systemEvent(
                projectId,
                AuditEventCategory.IMPORT,
                AuditEventTypes.IMPORT_SNAPSHOT_STORED,
                "project_snapshot",
                persisted.snapshot().id(),
                "Imported project snapshot",
                Map.of(),
                Map.of(
                        "snapshotVersion", persisted.snapshot().snapshotVersion(),
                        "taskCount", persisted.taskCount(),
                        "resourceCount", persisted.resourceCount(),
                        "assignmentCount", persisted.assignmentCount(),
                        "extendedAttributeCount", persisted.extendedAttributeCount()
                ),
                null,
                persisted.snapshot().id(),
                null,
                Map.of("imported", true, "projectWriteBack", false)
        ));
    }

    private void recordParseFailure(UUID projectId, UUID importBatchId, RuntimeException cause) {
        String failureReason = cause.getMessage() == null
                ? cause.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + ": " + cause.getMessage();

        try {
            importBatchService.recordParseFailure(importBatchId, failureReason);
        } catch (RuntimeException recordingFailure) {
            // Never let failure bookkeeping mask the original worker failure.
            cause.addSuppressed(recordingFailure);
            return;
        }

        auditEventRecorder.record(AuditEventCreateRequest.systemEvent(
                projectId,
                AuditEventCategory.IMPORT,
                AuditEventTypes.IMPORT_BATCH_PARSE_FAILED,
                "import_batch",
                importBatchId,
                "Import batch parse",
                Map.of("status", ImportBatchStatus.PARSING.databaseValue()),
                Map.of("status", ImportBatchStatus.FAILED.databaseValue()),
                failureReason,
                null,
                null,
                Map.of(
                        "parsed", false,
                        "workerCalled", true,
                        "imported", false,
                        "projectWriteBack", false,
                        "failureReason", failureReason
                )
        ));
    }
}
