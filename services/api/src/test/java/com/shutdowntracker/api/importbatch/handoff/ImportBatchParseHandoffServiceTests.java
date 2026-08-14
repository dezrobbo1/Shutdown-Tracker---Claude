package com.shutdowntracker.api.importbatch.handoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shutdowntracker.api.importbatch.ImportBatchCreateRequest;
import com.shutdowntracker.api.importbatch.ImportBatchParseSummaryUpdate;
import com.shutdowntracker.api.audit.AuditEventTypes;
import com.shutdowntracker.api.audit.CapturingAuditEventRecorder;
import com.shutdowntracker.api.importbatch.ImportBatchRecord;
import com.shutdowntracker.api.importbatch.ImportBatchRepository;
import com.shutdowntracker.api.importbatch.ImportBatchService;
import com.shutdowntracker.api.importbatch.ImportBatchStatus;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileKind;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataCreateRequest;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataRecord;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataRepository;
import com.shutdowntracker.api.sourcefile.metadata.SourceFileMetadataService;
import com.shutdowntracker.api.importedproject.ImportedAssignmentCreateRequest;
import com.shutdowntracker.api.importedproject.ImportedAssignmentRecord;
import com.shutdowntracker.api.importedproject.ImportedExtendedAttributeCreateRequest;
import com.shutdowntracker.api.importedproject.ImportedExtendedAttributeRecord;
import com.shutdowntracker.api.importedproject.ImportedProjectPersistenceService;
import com.shutdowntracker.api.importedproject.ImportedProjectRepository;
import com.shutdowntracker.api.importedproject.ImportedResourceCreateRequest;
import com.shutdowntracker.api.importedproject.ImportedResourceRecord;
import com.shutdowntracker.api.importedproject.ImportedTaskCreateRequest;
import com.shutdowntracker.api.importedproject.ImportedTaskRecord;
import com.shutdowntracker.api.importedproject.ProjectSnapshotCreateRequest;
import com.shutdowntracker.api.importedproject.ProjectSnapshotRecord;
import com.shutdowntracker.projectimport.contract.ParsedAssignment;
import com.shutdowntracker.projectimport.contract.ParsedExtendedAttribute;
import com.shutdowntracker.projectimport.contract.ParsedResource;
import com.shutdowntracker.projectimport.contract.ParsedTask;
import com.shutdowntracker.projectimport.contract.ProjectParseEntitiesResponse;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        assertThat(response.message())
                .describedAs("the parsed schedule is now stored, not discarded")
                .contains("Imported snapshot version 1 stored with 2 tasks")
                .contains("Microsoft Project write-back");

        // The child task must be linked to the parent row created moments earlier.
        ImportedTaskCreateRequest child = importedProjectRepository.tasks.get(1);
        assertThat(child.name()).isEqualTo("Remove guard");
        assertThat(child.parentImportedTaskId())
                .describedAs("hierarchy arrives as an external id and must resolve to a stored row")
                .isNotNull();

        // Assignments arrive with external identifiers on both sides and must be resolved.
        ImportedAssignmentCreateRequest assignment = importedProjectRepository.assignments.get(0);
        assertThat(assignment.importedTaskId()).isNotNull();
        assertThat(assignment.importedResourceId()).isNotNull();
        assertThat(auditEventRecorder.events().getLast().eventType())
                .isEqualTo(AuditEventTypes.IMPORT_SNAPSHOT_STORED);
    }

    @Test
    void recordsTerminalFailureWhenTheSnapshotCannotBeStored() {
        UUID projectId = UUID.randomUUID();
        UUID sourceFileId = UUID.randomUUID();
        UUID importBatchId = UUID.randomUUID();
        CapturingImportBatchRepository importBatchRepository = new CapturingImportBatchRepository(
                importBatch(importBatchId, projectId, sourceFileId, ImportBatchStatus.PENDING)
        );
        ImportBatchParseHandoffService service = new ImportBatchParseHandoffService(
                new ImportBatchService(importBatchRepository),
                new SourceFileMetadataService(new CapturingSourceFileMetadataRepository(
                        sourceFile(sourceFileId, projectId))),
                new ProjectParseHandoffService(new CapturingProjectParseJobClient(importBatchId)),
                new ImportedProjectPersistenceService(new RecordingImportedProjectRepository() {
                    @Override
                    public ProjectSnapshotRecord createSnapshot(ProjectSnapshotCreateRequest request) {
                        throw new IllegalStateException("Synthetic snapshot storage outage");
                    }
                }),
                auditEventRecorder
        );

        assertThatThrownBy(() -> service.requestParseSummary(projectId, importBatchId))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));

        // Without this the batch would sit in parsing forever after a successful parse.
        assertThat(importBatchRepository.failedImportBatchId).isEqualTo(importBatchId);
        assertThat(importBatchRepository.parseFailureReason).contains("Synthetic snapshot storage outage");
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
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY))
                .hasMessageContaining("Project worker parse request failed.");

        assertThat(importBatchRepository.statusUpdates).containsExactly(ImportBatchStatus.PARSING);
        assertThat(importBatchRepository.parseSummaryUpdate).isNull();
        // The batch must not sit in parsing with no explanation.
        assertThat(importBatchRepository.failedImportBatchId).isEqualTo(importBatchId);
        assertThat(importBatchRepository.parseFailureReason)
                .contains("Worker parse response referenced a different import batch.");
        assertThat(auditEventRecorder.events().getLast().eventType())
                .isEqualTo(AuditEventTypes.IMPORT_BATCH_PARSE_FAILED);
    }

    @Test
    void recordsTerminalFailureWhenTheWorkerCallThrows() {
        UUID projectId = UUID.randomUUID();
        UUID importBatchId = UUID.randomUUID();
        UUID sourceFileId = UUID.randomUUID();
        CapturingImportBatchRepository importBatchRepository = new CapturingImportBatchRepository(
                importBatch(importBatchId, projectId, sourceFileId, ImportBatchStatus.PENDING)
        );
        ImportBatchParseHandoffService service = service(
                importBatchRepository,
                new CapturingSourceFileMetadataRepository(sourceFile(sourceFileId, projectId)),
                failingClient(new IllegalStateException("Synthetic worker outage"))
        );

        assertThatThrownBy(() -> service.requestParseSummary(projectId, importBatchId))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));

        assertThat(importBatchRepository.failedImportBatchId).isEqualTo(importBatchId);
        assertThat(importBatchRepository.parseFailureReason).contains("Synthetic worker outage");
        assertThat(auditEventRecorder.events().getLast().metadata())
                .containsEntry("workerCalled", true)
                .containsEntry("parsed", false)
                .containsEntry("projectWriteBack", false);
    }

    private final CapturingAuditEventRecorder auditEventRecorder = new CapturingAuditEventRecorder();
    private final RecordingImportedProjectRepository importedProjectRepository =
            new RecordingImportedProjectRepository();

    private ImportBatchParseHandoffService service(
            ImportBatchRepository importBatchRepository,
            SourceFileMetadataRepository sourceFileRepository,
            ProjectParseJobClient jobClient
    ) {
        return new ImportBatchParseHandoffService(
                new ImportBatchService(importBatchRepository),
                new SourceFileMetadataService(sourceFileRepository),
                new ProjectParseHandoffService(jobClient),
                new ImportedProjectPersistenceService(importedProjectRepository),
                auditEventRecorder
        );
    }

    /** A parse client that fails, expressed as a class because the interface has two methods. */
    private static ProjectParseJobClient failingClient(RuntimeException failure) {
        return new ProjectParseJobClient() {
            @Override
            public ProjectParseSummaryResponse requestParseSummary(ProjectParseSummaryRequest request) {
                throw failure;
            }

            @Override
            public ProjectParseEntitiesResponse requestParseEntities(ProjectParseSummaryRequest request) {
                throw failure;
            }
        };
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

        private String parseFailureReason;
        private UUID failedImportBatchId;

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
        public ImportBatchRecord recordParseFailure(UUID importBatchId, String failureReason) {
            this.parseFailureReason = failureReason;
            this.failedImportBatchId = importBatchId;
            return new ImportBatchRecord(
                    importBatchId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ImportBatchStatus.FAILED,
                    null,
                    null,
                    0,
                    1
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
                    2,
                    1,
                    1,
                    1,
                    1,
                    1,
                    0,
                    1,
                    0,
                    List.of("Summary only; no schedule calculations were run.")
            );
        }

        @Override
        public ProjectParseEntitiesResponse requestParseEntities(ProjectParseSummaryRequest request) {
            ProjectParseSummaryResponse summary = requestParseSummary(request);
            return new ProjectParseEntitiesResponse(
                    summary,
                    "SYNTHETIC-PROJECT-1",
                    OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                    List.of(
                            new ParsedTask("1", "1", "Mechanical", null, "1", 0, true, null,
                                    null, null, null, null, null, null, null, Map.of()),
                            new ParsedTask("2", "2", "Remove guard", "1.1", "1.1", 1, false, "1",
                                    null, null, null, null, new BigDecimal("50"), null, null, Map.of())),
                    List.of(new ParsedResource("10", "Fitter", "WORK", Map.of("group", "CVM MECH"))),
                    List.of(new ParsedAssignment("100", "2", "10", Map.of())),
                    List.of(new ParsedExtendedAttribute("task", "2", "TEXT1", "Text1",
                            "Work Group", "CVM MECH", Map.of())));
        }
    }

    /**
     * Stores what it is given and hands back records with generated identifiers, which is
     * enough to check that the handoff resolves hierarchy and assignment links correctly.
     */
    private static class RecordingImportedProjectRepository implements ImportedProjectRepository {

        private final List<ImportedTaskCreateRequest> tasks = new ArrayList<>();
        private final List<ImportedAssignmentCreateRequest> assignments = new ArrayList<>();
        private ProjectSnapshotRecord snapshot;

        @Override
        public ProjectSnapshotRecord createSnapshot(ProjectSnapshotCreateRequest request) {
            snapshot = new ProjectSnapshotRecord(
                    UUID.randomUUID(),
                    request.projectId(),
                    request.importBatchId(),
                    request.status(),
                    request.externalProjectUid(),
                    request.externalProjectName(),
                    request.projectStatusDate(),
                    1);
            return snapshot;
        }

        @Override
        public List<ImportedTaskRecord> createTasks(
                UUID projectId, UUID snapshotId, List<ImportedTaskCreateRequest> requests) {
            List<ImportedTaskRecord> records = new ArrayList<>();
            for (ImportedTaskCreateRequest request : requests) {
                tasks.add(request);
                records.add(new ImportedTaskRecord(
                        UUID.randomUUID(), projectId, snapshotId,
                        request.externalUid(), request.name(), request.summary()));
            }
            return records;
        }

        @Override
        public List<ImportedResourceRecord> createResources(
                UUID projectId, UUID snapshotId, List<ImportedResourceCreateRequest> requests) {
            List<ImportedResourceRecord> records = new ArrayList<>();
            for (ImportedResourceCreateRequest request : requests) {
                records.add(new ImportedResourceRecord(
                        UUID.randomUUID(), projectId, snapshotId,
                        request.externalUid(), request.name(), request.resourceType()));
            }
            return records;
        }

        @Override
        public List<ImportedAssignmentRecord> createAssignments(
                UUID projectId, UUID snapshotId, List<ImportedAssignmentCreateRequest> requests) {
            List<ImportedAssignmentRecord> records = new ArrayList<>();
            for (ImportedAssignmentCreateRequest request : requests) {
                assignments.add(request);
                records.add(new ImportedAssignmentRecord(
                        UUID.randomUUID(), projectId, snapshotId, request.externalUid(),
                        request.taskExternalUid(), request.resourceExternalUid()));
            }
            return records;
        }

        @Override
        public List<ImportedExtendedAttributeRecord> createExtendedAttributes(
                UUID projectId, UUID snapshotId, List<ImportedExtendedAttributeCreateRequest> requests) {
            List<ImportedExtendedAttributeRecord> records = new ArrayList<>();
            for (ImportedExtendedAttributeCreateRequest request : requests) {
                records.add(new ImportedExtendedAttributeRecord(
                        UUID.randomUUID(), projectId, snapshotId, request.entityType(),
                        request.entityExternalUid(), request.fieldId(), request.fieldName()));
            }
            return records;
        }
    }
}
