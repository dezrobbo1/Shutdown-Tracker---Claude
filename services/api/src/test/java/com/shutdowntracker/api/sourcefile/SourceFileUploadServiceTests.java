package com.shutdowntracker.api.sourcefile;

import static org.assertj.core.api.Assertions.assertThat;

import com.shutdowntracker.api.audit.AuditEventCategory;
import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventTypes;
import com.shutdowntracker.api.audit.CapturingAuditEventRecorder;
import com.shutdowntracker.api.importbatch.ImportBatchCreateRequest;
import com.shutdowntracker.api.importbatch.ImportBatchRecord;
import com.shutdowntracker.api.importbatch.ImportBatchRepository;
import com.shutdowntracker.api.importbatch.ImportBatchService;
import com.shutdowntracker.api.importbatch.ImportBatchStatus;
import com.shutdowntracker.api.importbatch.ImportBatchParseSummaryUpdate;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileKind;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataCreateRequest;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataRecord;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataRepository;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataService;
import com.shutdowntracker.api.sourcefile.storage.SourceFileStorage;
import com.shutdowntracker.api.sourcefile.storage.SourceFileStorageRequest;
import com.shutdowntracker.api.sourcefile.storage.StoredSourceFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class SourceFileUploadServiceTests {

    @Test
    void storesSourceFileCreatesPendingImportBatchAndRecordsAudit() {
        UUID projectId = UUID.randomUUID();
        CapturingSourceFileStorage storage = new CapturingSourceFileStorage();
        CapturingSourceFileMetadataRepository metadataRepository = new CapturingSourceFileMetadataRepository();
        CapturingImportBatchRepository importBatchRepository = new CapturingImportBatchRepository();
        CapturingAuditEventRecorder auditRecorder = new CapturingAuditEventRecorder();
        SourceFileUploadService service = service(storage, metadataRepository, importBatchRepository, auditRecorder);

        SourceFileUploadResponse response = service.upload(projectId, multipartFile("synthetic-basic-wbs.mspdi.xml"));

        assertThat(response.accepted()).isTrue();
        assertThat(response.rejectionReason()).isNull();
        assertThat(response.sourceFile().projectId()).isEqualTo(projectId);
        assertThat(response.sourceFile().fileKind()).isEqualTo(SourceFileKind.MSPDI_XML);
        assertThat(response.importBatch().status()).isEqualTo(ImportBatchStatus.PENDING);
        assertThat(response.importBatch().sourceFileId()).isEqualTo(response.sourceFile().id());
        assertThat(response.message())
                .contains("pending import batch created")
                .contains("No file was parsed")
                .contains("written back to Microsoft Project");

        assertThat(storage.storeCalls).isEqualTo(1);
        assertThat(storage.request.originalFilename()).isEqualTo("synthetic-basic-wbs.mspdi.xml");
        assertThat(storage.request.sizeBytes()).isEqualTo(9);
        assertThat(metadataRepository.createRequest.projectId()).isEqualTo(projectId);
        assertThat(importBatchRepository.createRequest.projectId()).isEqualTo(projectId);
        assertThat(importBatchRepository.createRequest.sourceFileId()).isEqualTo(response.sourceFile().id());

        AuditEventCreateRequest auditEvent = auditRecorder.singleEvent();
        assertThat(auditEvent.eventCategory()).isEqualTo(AuditEventCategory.IMPORT);
        assertThat(auditEvent.eventType()).isEqualTo(AuditEventTypes.SOURCE_FILE_UPLOADED);
        assertThat(auditEvent.targetEntityType()).isEqualTo("source_file");
        assertThat(auditEvent.targetEntityId()).isEqualTo(response.sourceFile().id());
        assertThat(auditEvent.newValueSummary())
                .containsEntry("sourceFileId", response.sourceFile().id().toString())
                .containsEntry("importBatchId", response.importBatch().id().toString())
                .containsEntry("importBatchStatus", "pending");
        assertThat(auditEvent.metadata())
                .containsEntry("parsed", false)
                .containsEntry("workerCalled", false)
                .containsEntry("imported", false)
                .containsEntry("projectWriteBack", false);
    }

    @Test
    void rejectedValidationDoesNotStorePersistCreateBatchOrAudit() {
        UUID projectId = UUID.randomUUID();
        CapturingSourceFileStorage storage = new CapturingSourceFileStorage();
        CapturingSourceFileMetadataRepository metadataRepository = new CapturingSourceFileMetadataRepository();
        CapturingImportBatchRepository importBatchRepository = new CapturingImportBatchRepository();
        CapturingAuditEventRecorder auditRecorder = new CapturingAuditEventRecorder();
        SourceFileUploadService service = service(storage, metadataRepository, importBatchRepository, auditRecorder);

        SourceFileUploadResponse response = service.upload(projectId, multipartFile("unsafe.zip"));

        assertThat(response.accepted()).isFalse();
        assertThat(response.rejectionReason()).isEqualTo("Unsupported source file extension.");
        assertThat(response.sourceFile()).isNull();
        assertThat(response.importBatch()).isNull();
        assertThat(response.message()).contains("Upload rejected before storage");
        assertThat(storage.storeCalls).isZero();
        assertThat(metadataRepository.createRequest).isNull();
        assertThat(importBatchRepository.createRequest).isNull();
        assertThat(auditRecorder.events()).isEmpty();
    }

    private SourceFileUploadService service(
            SourceFileStorage storage,
            SourceFileMetadataRepository metadataRepository,
            ImportBatchRepository importBatchRepository,
            CapturingAuditEventRecorder auditRecorder
    ) {
        return new SourceFileUploadService(
                new SourceFileValidationService(new SourceFileValidationProperties(16)),
                storage,
                new SourceFileMetadataService(metadataRepository),
                new ImportBatchService(importBatchRepository),
                auditRecorder
        );
    }

    private MockMultipartFile multipartFile(String filename) {
        return new MockMultipartFile(
                "file",
                filename,
                "application/octet-stream",
                "synthetic".getBytes(StandardCharsets.UTF_8)
        );
    }

    private static class CapturingSourceFileStorage implements SourceFileStorage {

        private int storeCalls;
        private SourceFileStorageRequest request;

        @Override
        public StoredSourceFile store(SourceFileStorageRequest request) throws IOException {
            storeCalls++;
            this.request = request;
            return new StoredSourceFile(
                    "file:///synthetic/" + request.originalFilename(),
                    request.originalFilename(),
                    request.originalFilename(),
                    request.sizeBytes(),
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            );
        }
    }

    private static class CapturingSourceFileMetadataRepository implements SourceFileMetadataRepository {

        private SourceFileMetadataCreateRequest createRequest;

        @Override
        public SourceFileMetadataRecord create(SourceFileMetadataCreateRequest request) {
            createRequest = request;
            return new SourceFileMetadataRecord(
                    UUID.randomUUID(),
                    request.projectId(),
                    request.originalFilename(),
                    request.fileKind(),
                    request.storageUri(),
                    request.contentHash(),
                    request.sizeBytes()
            );
        }
    }

    private static class CapturingImportBatchRepository implements ImportBatchRepository {

        private ImportBatchCreateRequest createRequest;

        @Override
        public ImportBatchRecord create(ImportBatchCreateRequest request) {
            createRequest = request;
            return new ImportBatchRecord(
                    UUID.randomUUID(),
                    request.projectId(),
                    request.sourceFileId(),
                    ImportBatchStatus.PENDING,
                    null,
                    null,
                    0,
                    0
            );
        }

        @Override
        public ImportBatchRecord updateStatus(UUID importBatchId, ImportBatchStatus status) {
            throw new UnsupportedOperationException("Status updates are not part of upload orchestration.");
        }

        @Override
        public ImportBatchRecord recordParseSummary(ImportBatchParseSummaryUpdate update) {
            throw new UnsupportedOperationException("Parser summary writes are not part of upload orchestration.");
        }
    }
}
