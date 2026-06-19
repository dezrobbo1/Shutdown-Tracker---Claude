package com.shutdowntracker.api.sourcefile;

import com.shutdowntracker.api.audit.AuditEventCategory;
import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventRecorder;
import com.shutdowntracker.api.audit.AuditEventTypes;
import com.shutdowntracker.api.importbatch.ImportBatchRecord;
import com.shutdowntracker.api.importbatch.ImportBatchService;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataRecord;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataService;
import com.shutdowntracker.api.sourcefile.storage.SourceFileStorage;
import com.shutdowntracker.api.sourcefile.storage.SourceFileStorageRequest;
import com.shutdowntracker.api.sourcefile.storage.StoredSourceFile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class SourceFileUploadService {

    private final SourceFileValidationService validationService;
    private final SourceFileStorage storage;
    private final SourceFileMetadataService metadataService;
    private final ImportBatchService importBatchService;
    private final AuditEventRecorder auditEventRecorder;

    public SourceFileUploadService(
            SourceFileValidationService validationService,
            SourceFileStorage storage,
            SourceFileMetadataService metadataService,
            ImportBatchService importBatchService,
            AuditEventRecorder auditEventRecorder
    ) {
        this.validationService = validationService;
        this.storage = storage;
        this.metadataService = metadataService;
        this.importBatchService = importBatchService;
        this.auditEventRecorder = auditEventRecorder;
    }

    @Transactional
    public SourceFileUploadResponse upload(UUID projectId, MultipartFile file) {
        Objects.requireNonNull(projectId, "projectId is required.");
        Objects.requireNonNull(file, "file is required.");

        SourceFileValidationResponse validation = validationService.validate(file);
        if (!validation.accepted()) {
            return SourceFileUploadResponse.rejected(validation);
        }

        StoredSourceFile storedSourceFile = store(file, validation);
        SourceFileMetadataRecord sourceFile = metadataService.create(projectId, storedSourceFile);
        ImportBatchRecord importBatch = importBatchService.createPending(projectId, sourceFile.id());

        auditEventRecorder.record(uploadAuditEvent(projectId, sourceFile, importBatch));

        return SourceFileUploadResponse.accepted(validation, sourceFile, importBatch);
    }

    private StoredSourceFile store(MultipartFile file, SourceFileValidationResponse validation) {
        try {
            return storage.store(new SourceFileStorageRequest(
                    validation.originalFilename(),
                    file.getInputStream(),
                    validation.sizeBytes()
            ));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to store source file upload.", exception);
        }
    }

    private AuditEventCreateRequest uploadAuditEvent(
            UUID projectId,
            SourceFileMetadataRecord sourceFile,
            ImportBatchRecord importBatch
    ) {
        return AuditEventCreateRequest.systemEvent(
                projectId,
                AuditEventCategory.IMPORT,
                AuditEventTypes.SOURCE_FILE_UPLOADED,
                "source_file",
                sourceFile.id(),
                sourceFile.originalFilename(),
                Map.of(),
                Map.of(
                        "sourceFileId", sourceFile.id().toString(),
                        "importBatchId", importBatch.id().toString(),
                        "originalFilename", sourceFile.originalFilename(),
                        "fileKind", sourceFile.fileKind().databaseValue(),
                        "sizeBytes", sourceFile.sizeBytes(),
                        "contentHash", sourceFile.contentHash(),
                        "importBatchStatus", importBatch.status().databaseValue()
                ),
                "Source file uploaded and pending import batch created.",
                null,
                null,
                Map.of(
                        "parsed", false,
                        "workerCalled", false,
                        "imported", false,
                        "projectWriteBack", false
                )
        );
    }
}
