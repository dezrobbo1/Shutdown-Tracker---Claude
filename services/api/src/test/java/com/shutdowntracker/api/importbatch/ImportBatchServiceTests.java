package com.shutdowntracker.api.importbatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportBatchServiceTests {

    @Test
    void createsPendingImportBatchForSourceFile() {
        UUID projectId = UUID.randomUUID();
        UUID sourceFileId = UUID.randomUUID();
        FakeImportBatchRepository repository = new FakeImportBatchRepository();
        ImportBatchService service = new ImportBatchService(repository);

        ImportBatchRecord record = service.createPending(projectId, sourceFileId);

        assertThat(repository.createRequest.projectId()).isEqualTo(projectId);
        assertThat(repository.createRequest.sourceFileId()).isEqualTo(sourceFileId);
        assertThat(record.projectId()).isEqualTo(projectId);
        assertThat(record.sourceFileId()).isEqualTo(sourceFileId);
        assertThat(record.status()).isEqualTo(ImportBatchStatus.PENDING);
        assertThat(record.warningCount()).isZero();
        assertThat(record.errorCount()).isZero();
    }

    @Test
    void updatesImportBatchStatusUsingExistingEnumValues() {
        UUID importBatchId = UUID.randomUUID();
        FakeImportBatchRepository repository = new FakeImportBatchRepository();
        ImportBatchService service = new ImportBatchService(repository);

        ImportBatchRecord record = service.updateStatus(importBatchId, ImportBatchStatus.PARSING);

        assertThat(repository.updatedImportBatchId).isEqualTo(importBatchId);
        assertThat(repository.updatedStatus).isEqualTo(ImportBatchStatus.PARSING);
        assertThat(record.status()).isEqualTo(ImportBatchStatus.PARSING);
    }

    private static class FakeImportBatchRepository implements ImportBatchRepository {

        private ImportBatchCreateRequest createRequest;
        private UUID updatedImportBatchId;
        private ImportBatchStatus updatedStatus;

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
            updatedImportBatchId = importBatchId;
            updatedStatus = status;
            return new ImportBatchRecord(
                    importBatchId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    status,
                    null,
                    null,
                    0,
                    0
            );
        }
    }
}
