package com.shutdowntracker.api.exportpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shutdowntracker.api.audit.CapturingAuditEventRecorder;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.exportpreview.handoff.DisconnectedProjectExportArtifactJobClient;
import com.shutdowntracker.api.exportpreview.handoff.ExportArtifactGenerationRequest;
import com.shutdowntracker.api.exportpreview.handoff.ExportArtifactGenerationResponse;
import com.shutdowntracker.api.exportpreview.handoff.ExportArtifactHandoffService;
import com.shutdowntracker.api.exportpreview.handoff.ProjectExportArtifactJobClient;
import com.shutdowntracker.api.exportpreview.storage.ExportArtifactStorage;
import com.shutdowntracker.api.exportpreview.storage.ExportArtifactStorageLocation;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactField;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactFieldValue;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationResponse;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSummary;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactTask;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ExportArtifactHandoffServiceTests {

    private static final Actor ACTOR =
            new Actor(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "planner", "Synthetic Planner");


    @Test
    void generatesWorkerArtifactForApprovedEligibleLeafLinesAndRecordsMetadata() {
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository();
        CapturingProjectExportArtifactJobClient client = new CapturingProjectExportArtifactJobClient();
        CapturingExportArtifactStorage storage = new CapturingExportArtifactStorage();
        ExportArtifactHandoffService service = service(repository, client, storage);

        ExportArtifactGenerationResponse response = service.generateArtifact(
                repository.projectId,
                repository.exportBatchId,
                ACTOR,
                new ExportArtifactGenerationRequest(
                        "Synthetic worker generation",
                        Map.of("requestedBy", "test")
                )
        );

        assertThat(client.request.exportBatchId()).isEqualTo(repository.exportBatchId);
        assertThat(client.request.projectId()).isEqualTo(repository.projectId);
        assertThat(storage.location.projectId()).isEqualTo(repository.projectId);
        assertThat(storage.location.exportBatchId()).isEqualTo(repository.exportBatchId);
        assertThat(storage.location.storageKind()).isEqualTo("local_filesystem");
        assertThat(client.request.outputPath()).contains(repository.projectId.toString());
        assertThat(client.request.outputPath()).endsWith(repository.exportBatchId + ".mspdi.xml");
        assertThat(client.request.artifactRequest().projectName())
                .isEqualTo("Shutdown Tracker Export Batch " + repository.exportBatchId);
        assertThat(client.request.artifactRequest().tasks()).hasSize(1);
        assertThat(client.request.artifactRequest().tasks().getFirst().microsoftProjectTaskUid()).isEqualTo("101");
        assertThat(client.request.artifactRequest().tasks().getFirst().microsoftProjectTaskId()).isEqualTo("1");
        assertThat(client.request.artifactRequest().tasks().getFirst().fieldValues()).hasSize(2);
        assertThat(response.exportPreview().batch().status()).isEqualTo(ExportBatchState.GENERATED);
        assertThat(response.exportPreview().batch().generatedByUserId()).isEqualTo(ACTOR.userId());
        assertThat(response.exportPreview().batch().exportFileUri()).isEqualTo(storage.location.storageUri());
        assertThat(response.exportPreview().batch().exportFileHash()).isEqualTo(client.response.exportFileHash());
        assertThat(response.message()).contains("No Microsoft Project write-back");
    }

    @Test
    void rejectsNonApprovedBatchesBeforeWorkerCall() {
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository();
        repository.status = ExportBatchState.DRAFT_PREVIEW;
        CapturingProjectExportArtifactJobClient client = new CapturingProjectExportArtifactJobClient();
        ExportArtifactHandoffService service = service(repository, client);

        assertThatThrownBy(() -> service.generateArtifact(repository.projectId, repository.exportBatchId, ACTOR, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Only approved export batches can request worker artifact generation.");
        assertThat(client.request).isNull();
    }

    @Test
    void rejectsMissingMicrosoftProjectTaskIdentityBeforeWorkerCall() {
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository();
        repository.lines = List.of(repository.eligibleLine(null, "1"));
        CapturingProjectExportArtifactJobClient client = new CapturingProjectExportArtifactJobClient();
        ExportArtifactHandoffService service = service(repository, client);

        assertThatThrownBy(() -> service.generateArtifact(repository.projectId, repository.exportBatchId, ACTOR, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Imported task external UID is required for export artifacts.");
        assertThat(client.request).isNull();
    }

    @Test
    void rejectsWorkerArtifactUriOutsideReservedStorageLocation() {
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository();
        CapturingProjectExportArtifactJobClient client = new CapturingProjectExportArtifactJobClient();
        client.mismatchedUri = true;
        ExportArtifactHandoffService service = service(repository, client);

        assertThatThrownBy(() -> service.generateArtifact(repository.projectId, repository.exportBatchId, ACTOR, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Worker export artifact response did not match the reserved storage URI.");
        // The batch must not remain approved after a terminal generation failure.
        assertThat(repository.status).isEqualTo(ExportBatchState.FAILED);
        assertThat(repository.failureReason)
                .contains("Worker export artifact response did not match the reserved storage URI.");
    }

    @Test
    void defaultClientKeepsApiDisconnectedFromExportGeneration() {
        DisconnectedProjectExportArtifactJobClient client = new DisconnectedProjectExportArtifactJobClient();
        ProjectExportArtifactGenerationRequest request = new FakeExportPreviewRepository().workerRequest();

        assertThatThrownBy(() -> client.generateArtifact(request))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("services/project-worker")
                .hasMessageContaining("the API does not generate files")
                .hasMessageContaining("write back to Microsoft Project");
    }

    private ExportArtifactHandoffService service(
            FakeExportPreviewRepository repository,
            ProjectExportArtifactJobClient client
    ) {
        return service(repository, client, new CapturingExportArtifactStorage());
    }

    private ExportArtifactHandoffService service(
            FakeExportPreviewRepository repository,
            ProjectExportArtifactJobClient client,
            ExportArtifactStorage storage
    ) {
        return new ExportArtifactHandoffService(
                new ExportPreviewService(repository, new CapturingAuditEventRecorder()),
                client,
                storage
        );
    }

    private static class CapturingProjectExportArtifactJobClient implements ProjectExportArtifactJobClient {

        private ProjectExportArtifactGenerationRequest request;
        private ProjectExportArtifactGenerationResponse response;
        private boolean mismatchedUri;

        @Override
        public ProjectExportArtifactGenerationResponse generateArtifact(ProjectExportArtifactGenerationRequest request) {
            this.request = request;
            String exportFileUri = mismatchedUri
                    ? "file:///unexpected/export-artifacts/" + request.exportBatchId() + ".mspdi.xml"
                    : Path.of(request.outputPath()).toUri().toString();
            this.response = new ProjectExportArtifactGenerationResponse(
                    request.exportBatchId(),
                    request.projectId(),
                    exportFileUri,
                    "synthetic-sha256",
                    new ProjectExportArtifactSummary(
                            request.exportBatchId() + ".mspdi.xml",
                            "mspdi_xml",
                            request.artifactRequest().tasks().size(),
                            request.artifactRequest().tasks().stream()
                                    .mapToInt(task -> task.fieldValues().size())
                                    .sum(),
                            512,
                            "synthetic-sha256",
                            List.of("MSPDI/XML artifact only; no schedule calculations or Microsoft Project write-back were run.")
                    ),
                    "MSPDI/XML artifact generated by project worker. No Microsoft Project write-back was run."
            );
            return response;
        }
    }

    private static class CapturingExportArtifactStorage implements ExportArtifactStorage {

        private ExportArtifactStorageLocation location;

        @Override
        public ExportArtifactStorageLocation prepareExportArtifact(UUID projectId, UUID exportBatchId) {
            Path outputPath = Path.of(
                    ".shutdown-tracker",
                    "export-artifacts",
                    projectId.toString(),
                    exportBatchId + ".mspdi.xml"
            ).toAbsolutePath().normalize();
            location = new ExportArtifactStorageLocation(
                    projectId,
                    exportBatchId,
                    exportBatchId + ".mspdi.xml",
                    outputPath,
                    outputPath.toUri().toString(),
                    "local_filesystem"
            );
            return location;
        }
    }

    private static class FakeExportPreviewRepository implements ExportPreviewRepository {

        private String failureReason;

        private final UUID projectId = UUID.randomUUID();
        private final UUID projectSnapshotId = UUID.randomUUID();
        private final UUID exportBatchId = UUID.randomUUID();
        private final UUID importedTaskId = UUID.randomUUID();
        private ExportBatchState status = ExportBatchState.APPROVED;
        private UUID generatedByUserId;
        private OffsetDateTime generatedAt;
        private String exportFileUri;
        private String exportFileHash;
        private OffsetDateTime verifiedAt;
        private UUID verifiedByUserId;
        private List<ExportPreviewLineRecord> lines = List.of(
                eligibleLine("101", "1"),
                new ExportPreviewLineRecord(
                        UUID.randomUUID(),
                        exportBatchId,
                        projectId,
                        projectSnapshotId,
                        importedTaskId,
                        "101",
                        "1",
                        "Synthetic Task A1",
                        "task_update",
                        UUID.randomUUID(),
                        ApprovalState.APPROVED_FOR_EXPORT,
                        "actual_start",
                        null,
                        "2026-01-05T07:00:00Z",
                        UUID.randomUUID(),
                        OffsetDateTime.parse("2026-01-01T07:00:00Z"),
                        "Synthetic reason",
                        true,
                        true
                ),
                new ExportPreviewLineRecord(
                        UUID.randomUUID(),
                        exportBatchId,
                        projectId,
                        projectSnapshotId,
                        UUID.randomUUID(),
                        "202",
                        "2",
                        "Synthetic Summary",
                        "task_update",
                        UUID.randomUUID(),
                        ApprovalState.APPROVED_FOR_EXPORT,
                        "actual_finish",
                        null,
                        "2026-01-06T15:00:00Z",
                        UUID.randomUUID(),
                        OffsetDateTime.parse("2026-01-01T07:00:00Z"),
                        "Synthetic reason",
                        false,
                        false
                )
        );

        private ExportPreviewLineRecord eligibleLine(String externalUid, String externalId) {
            return new ExportPreviewLineRecord(
                    UUID.randomUUID(),
                    exportBatchId,
                    projectId,
                    projectSnapshotId,
                    importedTaskId,
                    externalUid,
                    externalId,
                    "Synthetic Task A1",
                    "task_update",
                    UUID.randomUUID(),
                    ApprovalState.APPROVED_FOR_EXPORT,
                    "percent_complete",
                    "25",
                    "75",
                    UUID.randomUUID(),
                    OffsetDateTime.parse("2026-01-01T07:00:00Z"),
                    "Synthetic reason",
                    true,
                    true
            );
        }

        private ProjectExportArtifactGenerationRequest workerRequest() {
            return new ProjectExportArtifactGenerationRequest(
                    exportBatchId,
                    projectId,
                    ".shutdown-tracker/export-artifacts/" + exportBatchId + ".mspdi.xml",
                    new ProjectExportArtifactRequest(
                            "Synthetic Export Preview",
                            List.of(new ProjectExportArtifactTask(
                                    importedTaskId.toString(),
                                    "101",
                                    "1",
                                    "Synthetic Task A1",
                                    true,
                                    List.of(new ProjectExportArtifactFieldValue(
                                            ProjectExportArtifactField.PERCENT_COMPLETE,
                                            "75"
                                    ))
                            ))
                    )
            );
        }

        @Override
        public ExportPreviewBatchRecord createDraftPreview(
                UUID projectId,
                UUID projectSnapshotId,
                Map<String, Object> metadata
        ) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public Optional<ExportPreviewBatchRecord> findBatch(UUID projectId, UUID exportBatchId) {
            if (!this.projectId.equals(projectId) || !this.exportBatchId.equals(exportBatchId)) {
                return Optional.empty();
            }
            return Optional.of(batch());
        }

        @Override
        public Optional<ExportPreviewBatchRecord> approveBatch(
                UUID projectId,
                UUID exportBatchId,
                UUID approvedByUserId,
                Map<String, Object> metadata
        ) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public Optional<ExportPreviewBatchRecord> rejectBatch(
                UUID projectId,
                UUID exportBatchId,
                Map<String, Object> metadata
        ) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public Optional<ExportPreviewBatchRecord> markBatchGenerated(
                UUID projectId,
                UUID exportBatchId,
                String exportFileUri,
                String exportFileHash,
                UUID generatedByUserId,
                Map<String, Object> metadata
        ) {
            if (status != ExportBatchState.APPROVED) {
                return Optional.empty();
            }
            status = ExportBatchState.GENERATED;
            generatedAt = OffsetDateTime.parse("2026-01-01T02:00:00Z");
            this.generatedByUserId = generatedByUserId;
            this.exportFileUri = exportFileUri;
            this.exportFileHash = exportFileHash;
            return Optional.of(batch());
        }

        @Override
        public Optional<ExportPreviewBatchRecord> markBatchOpenedInMicrosoftProject(
                UUID projectId,
                UUID exportBatchId,
                Map<String, Object> metadata
        ) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public Optional<ExportPreviewBatchRecord> markBatchVerified(
                UUID projectId,
                UUID exportBatchId,
                UUID verifiedByUserId,
                Map<String, Object> metadata
        ) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public Optional<ExportPreviewBatchRecord> markBatchFailed(
                UUID projectId,
                UUID exportBatchId,
                String failureReason,
                Map<String, Object> metadata
        ) {
            if (status == ExportBatchState.FAILED || status == ExportBatchState.SUPERSEDED) {
                return Optional.empty();
            }
            status = ExportBatchState.FAILED;
            this.failureReason = failureReason;
            return findBatch(projectId, exportBatchId);
        }

        @Override
        public Optional<ExportPreviewTaskContext> findTaskContext(
                UUID projectId,
                UUID projectSnapshotId,
                UUID importedTaskId
        ) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public Optional<ApprovalState> findLatestApprovalState(
                UUID projectId,
                String sourceEntityType,
                UUID sourceEntityId
        ) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public ExportPreviewLineRecord createLine(
                UUID projectId,
                UUID projectSnapshotId,
                UUID exportBatchId,
                ExportPreviewMaterializedLine line
        ) {
            throw new UnsupportedOperationException("not needed");
        }

        @Override
        public List<ExportPreviewLineRecord> listLines(UUID projectId, UUID exportBatchId) {
            return List.copyOf(lines);
        }

        private ExportPreviewBatchRecord batch() {
            int eligible = (int) lines.stream().filter(ExportPreviewLineRecord::exportEligible).count();
            return new ExportPreviewBatchRecord(
                    exportBatchId,
                    projectId,
                    projectSnapshotId,
                    status,
                    OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                    OffsetDateTime.parse("2026-01-01T01:00:00Z"),
                    null,
                    generatedAt,
                    generatedByUserId,
                    verifiedAt,
                    verifiedByUserId,
                    exportFileUri,
                    exportFileHash,
                    null,
                    lines.size(),
                    eligible,
                    lines.size() - eligible
            );
        }
    }
}
