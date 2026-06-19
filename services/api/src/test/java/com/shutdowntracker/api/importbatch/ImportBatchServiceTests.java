package com.shutdowntracker.api.importbatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.util.List;
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

    @Test
    void recordsParsedImportSummaryUsingExistingImportBatchColumns() {
        UUID importBatchId = UUID.randomUUID();
        FakeImportBatchRepository repository = new FakeImportBatchRepository();
        ImportBatchService service = new ImportBatchService(repository);

        ImportBatchRecord record = service.recordParsedSummary(new ProjectParseSummaryResponse(
                importBatchId,
                "mpxj",
                "16.4.0",
                "synthetic-basic-wbs.mspdi.xml",
                "mspdi_xml",
                "Synthetic Basic WBS",
                6,
                2,
                4,
                0,
                0,
                1,
                0,
                1,
                0,
                List.of(
                        "Summary only; no schedule calculations were run.",
                        "Ignored read issue: Synthetic warning"
                )
        ));

        assertThat(repository.parseSummaryUpdate.importBatchId()).isEqualTo(importBatchId);
        assertThat(repository.parseSummaryUpdate.parserName()).isEqualTo("mpxj");
        assertThat(repository.parseSummaryUpdate.parserVersion()).isEqualTo("16.4.0");
        assertThat(repository.parseSummaryUpdate.warningCount()).isEqualTo(1);
        assertThat(repository.parseSummaryUpdate.errorCount()).isZero();
        assertThat(repository.parseSummaryUpdate.parseSummary().summaryOnly()).isTrue();
        assertThat(repository.parseSummaryUpdate.parseSummary().counts().taskCount()).isEqualTo(6);
        assertThat(repository.parseSummaryUpdate.parseSummary().counts().summaryTaskCount()).isEqualTo(2);
        assertThat(repository.parseSummaryUpdate.parseSummary().counts().leafTaskCount()).isEqualTo(4);
        assertThat(repository.parseSummaryUpdate.parseSummary().notes())
                .contains("Summary only; no schedule calculations were run.");
        assertThat(record.status()).isEqualTo(ImportBatchStatus.PARSED);
        assertThat(record.parserName()).isEqualTo("mpxj");
        assertThat(record.warningCount()).isEqualTo(1);
    }

    private static class FakeImportBatchRepository implements ImportBatchRepository {

        private ImportBatchCreateRequest createRequest;
        private UUID updatedImportBatchId;
        private ImportBatchStatus updatedStatus;
        private ImportBatchParseSummaryUpdate parseSummaryUpdate;

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

        @Override
        public ImportBatchRecord recordParseSummary(ImportBatchParseSummaryUpdate update) {
            parseSummaryUpdate = update;
            return new ImportBatchRecord(
                    update.importBatchId(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ImportBatchStatus.PARSED,
                    update.parserName(),
                    update.parserVersion(),
                    update.warningCount(),
                    update.errorCount()
            );
        }
    }
}
