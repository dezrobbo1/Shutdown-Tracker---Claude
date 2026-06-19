package com.shutdowntracker.api.importbatch.handoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shutdowntracker.api.importbatch.ImportBatchCreateRequest;
import com.shutdowntracker.api.importbatch.ImportBatchParseSummaryUpdate;
import com.shutdowntracker.api.importbatch.ImportBatchRecord;
import com.shutdowntracker.api.importbatch.ImportBatchRepository;
import com.shutdowntracker.api.importbatch.ImportBatchService;
import com.shutdowntracker.api.importbatch.ImportBatchStatus;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileKind;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataCreateRequest;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataRecord;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataRepository;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataService;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ImportBatchParseHandoffServiceTests {

    @Test
    void requestsWorkerSummaryForPendingImportBatchAndRecordsParsedSummary() {
        UUID projectId = UUID.randomUUID();
        UUID sourceFileId = UUID.randomUUID();
        UUID importBatchId = UUID.randomUUID();
        CapturingImportBatchRepository importBatchRepository = new CapturingImportBatchRepository(
                importBatch(importBatchId, projectId, sourceFileId, ImportBatchStatus.PENDING)
        );
        CapturingSourceFileMetadataRepository sourceFileRepository = new CapturingSourceFileMetadataRepository(
                sourceFile(sourceFileId, projectId)
        );
        CapturingProjectParseJobClient jobClient = new CapturingProjectParseJobClient(importBatchId);
        ImportBatchParseHandoffService service = service(importBatchRepository, sourceFileRepository, jobClient);

        ImportBatchParseHandoffResponse response = service.requestParseSummary(projectId, importBatchId);

        assertThat(importBatchRepository.statusUpdates).containsExactly(ImportBatchStatus.PARSING);
        assertThat(jobClient.request.importBatchId()).isEqualTo(importBatchId);
        assertThat(jobClient.request.projectId()).isEqualTo(projectId);
        assertThat(jobClient.request.sourceFileId()).isEqualTo(sourceFileId);
        assertThat(jobClient.request.storageUri()).isEqualTo("file:///synthetic/source/synthetic-basic-wbs.mspdi.xml");
        assertThat(importBatchRepository.parseSummaryUpdate.importBatchId()).isEqualTo(importBatchId);
        assertThat(importBatchRepository.parseSummaryUpdate.parseSummary().summaryOnly()).isTrue();
        assertThat(response.importBatch().status()).isEqualTo(ImportBatchStatus.PARSED);
        assertThat(response.parseSummary().notes()).contains("Summary only; no schedule calculations were run.");
        assertThat(response.message())
                .contains("No imported snapshot")
                .contains("Microsoft Project write-back");
    }

    @Test
    void rejectsImportBatchThatIsNotPendingBeforeCallingWorker() {
        UUID projectId = UUID.randomUUID();
        UUID sourceFileId = UUID.randomUUID();
        UUID importBatchId = UUID.randomUUID();
        CapturingImportBatchRepository importBatchRepository = new CapturingImportBatchRepository(
                importBatch(importBatchId, projectId, sourceFileId, ImportBatchStatus.PARSED)
        );
        CapturingProjectParseJobClient jobClient = new CapturingProjectParseJobClient(importBatchId);
        ImportBatchParseHandoffService service = service(
                importBatchRepository,
                new CapturingSourceFileMetadataRepository(sourceFile(sourceFileId, projectId)),
                jobClient
        );

        assertThatThrownBy(() -> service.requestParseSummary(projectId, importBatchId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(jobClient.request).isNull();
        assertThat(importBatchRepository.statusUpdates).isEmpty();
        assertThat(importBatchRepository.parseSummaryUpdate).isNull();
    }

    @Test
    void rejectsWorkerResponseForDifferentImportBatch() {
        UUID projectId = UUID.randomUUID();
        UUID sourceFileId = UUID.randomUUID();
        UUID importBatchId = UUID.randomUUID();
        CapturingImportBatchRepository importBatchRepository = new CapturingImportBatchRepository(
                importBatch(importBatchId, projectId, sourceFileId, ImportBatchStatus.PENDING)
        );
        CapturingProjectParseJobClient jobClient = new CapturingProjectParseJobClient(UUID.randomUUID());
        ImportBatchParseHandoffService service = service(
                importBatchRepository,
                new CapturingSourceFileMetadataRepository(sourceFile(sourceFileId, projectId)),
                jobClient
        );

        assertThatThrownBy(() -> service.requestParseSummary(projectId, importBatchId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Worker parse response referenced a different import batch.");

        assertThat(importBatchRepository.statusUpdates).containsExactly(ImportBatchStatus.PARSING);
        assertThat(importBatchRepository.parseSummaryUpdate).isNull();
    }

    private ImportBatchParseHandoffService service(
            ImportBatchRepository importBatchRepository,
            SourceFileMetadataRepository sourceFileRepository,
            ProjectParseJobClient jobClient
    ) {
        return new ImportBatchParseHandoffService(
                new ImportBatchService(importBatchRepository),
                new SourceFileMetadataService(sourceFileRepository),
                new ProjectParseHandoffService(jobClient)
        );
    }

    private ImportBatchRecord importBatch(
            UUID importBatchId,
            UUID projectId,
            UUID sourceFileId,
            ImportBatchStatus status
    ) {
        return new ImportBatchRecord(importBatchId, projectId, sourceFileId, status, null, null, 0, 0);
    }

    private SourceFileMetadataRecord sourceFile(UUID sourceFileId, UUID projectId) {
        return new SourceFileMetadataRecord(
                sourceFileId,
                projectId,
                "synthetic-basic-wbs.mspdi.xml",
                SourceFileKind.MSPDI_XML,
                "file:///synthetic/source/synthetic-basic-wbs.mspdi.xml",
                "synthetic-hash",
                512
        );
    }

    private static class CapturingImportBatchRepository implements ImportBatchRepository {

        private final ImportBatchRecord findRecord;
        private final List<ImportBatchStatus> statusUpdates = new ArrayList<>();
        private ImportBatchParseSummaryUpdate parseSummaryUpdate;

        private CapturingImportBatchRepository(ImportBatchRecord findRecord) {
            this.findRecord = findRecord;
        }

        @Override
        public Optional<ImportBatchRecord> findByProjectIdAndId(UUID projectId, UUID importBatchId) {
            return Optional.ofNullable(findRecord)
                    .filter(record -> record.projectId().equals(projectId) && record.id().equals(importBatchId));
        }

        @Override
        public ImportBatchRecord create(ImportBatchCreateRequest request) {
            throw new UnsupportedOperationException("Import batch creation is not part of parse handoff.");
        }

        @Override
        public ImportBatchRecord updateStatus(UUID importBatchId, ImportBatchStatus status) {
            statusUpdates.add(status);
            return new ImportBatchRecord(
                    importBatchId,
                    findRecord.projectId(),
                    findRecord.sourceFileId(),
                    status,
                    findRecord.parserName(),
                    findRecord.parserVersion(),
                    findRecord.warningCount(),
                    findRecord.errorCount()
            );
        }

        @Override
        public ImportBatchRecord recordParseSummary(ImportBatchParseSummaryUpdate update) {
            parseSummaryUpdate = update;
            return new ImportBatchRecord(
                    update.importBatchId(),
                    findRecord.projectId(),
                    findRecord.sourceFileId(),
                    ImportBatchStatus.PARSED,
                    update.parserName(),
                    update.parserVersion(),
                    update.warningCount(),
                    update.errorCount()
            );
        }
    }

    private static class CapturingSourceFileMetadataRepository implements SourceFileMetadataRepository {

        private final SourceFileMetadataRecord findRecord;

        private CapturingSourceFileMetadataRepository(SourceFileMetadataRecord findRecord) {
            this.findRecord = findRecord;
        }

        @Override
        public Optional<SourceFileMetadataRecord> findByProjectIdAndId(UUID projectId, UUID sourceFileId) {
            return Optional.ofNullable(findRecord)
                    .filter(record -> record.projectId().equals(projectId) && record.id().equals(sourceFileId));
        }

        @Override
        public SourceFileMetadataRecord create(SourceFileMetadataCreateRequest request) {
            throw new UnsupportedOperationException("Source file creation is not part of parse handoff.");
        }
    }

    private static class CapturingProjectParseJobClient implements ProjectParseJobClient {

        private final UUID responseImportBatchId;
        private ProjectParseSummaryRequest request;

        private CapturingProjectParseJobClient(UUID responseImportBatchId) {
            this.responseImportBatchId = responseImportBatchId;
        }

        @Override
        public ProjectParseSummaryResponse requestParseSummary(ProjectParseSummaryRequest request) {
            this.request = request;
            return new ProjectParseSummaryResponse(
                    responseImportBatchId,
                    "mpxj",
                    "16.4.0",
                    request.originalFilename(),
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
                    List.of("Summary only; no schedule calculations were run.")
            );
        }
    }
}
