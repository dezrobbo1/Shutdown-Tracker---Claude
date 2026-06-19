package com.shutdowntracker.api.exportpreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventTypes;
import com.shutdowntracker.api.audit.CapturingAuditEventRecorder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ExportPreviewServiceTests {

    @Test
    void createsDraftPreviewWithEligibleApprovedLeafTaskLine() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);

        ExportPreviewDetail detail = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                Map.of("source", "synthetic-export-preview")
        ));
        AuditEventCreateRequest event = audit.singleEvent();

        assertThat(repository.createBatchProjectId).isEqualTo(projectId);
        assertThat(repository.createBatchProjectSnapshotId).isEqualTo(repository.projectSnapshotId);
        assertThat(detail.batch().status()).isEqualTo(ExportBatchState.DRAFT_PREVIEW);
        assertThat(detail.batch().lineCount()).isEqualTo(1);
        assertThat(detail.batch().eligibleLineCount()).isEqualTo(1);
        assertThat(detail.lines()).hasSize(1);
        assertThat(detail.lines().getFirst().oldValue()).isEqualTo("25.00");
        assertThat(detail.lines().getFirst().newValue()).isEqualTo("50");
        assertThat(detail.lines().getFirst().approvalState()).isEqualTo(ApprovalState.APPROVED_FOR_EXPORT);
        assertThat(detail.lines().getFirst().leafTask()).isTrue();
        assertThat(detail.lines().getFirst().exportEligible()).isTrue();
        assertThat(detail.message()).contains("No MSPDI/XML artifact was generated");
        assertThat(event.eventType()).isEqualTo(AuditEventTypes.EXPORT_PREVIEW_CREATED);
        assertThat(event.projectId()).isEqualTo(projectId);
        assertThat(event.projectSnapshotId()).isEqualTo(repository.projectSnapshotId);
        assertThat(event.exportBatchId()).isEqualTo(detail.batch().id());
        assertThat(event.oldValueSummary()).containsEntry("status", "none");
        assertThat(event.newValueSummary()).containsEntry("status", "draft_preview");
        assertThat(event.metadata())
                .containsEntry("lineCount", 1)
                .containsEntry("eligibleLineCount", 1)
                .containsEntry("ineligibleLineCount", 0)
                .containsEntry("artifactGenerated", false)
                .containsEntry("projectWriteBack", false);
    }

    @Test
    void keepsApprovedSummaryTaskLineIneligible() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());

        ExportPreviewDetail detail = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.summaryTaskId, repository.approvedSourceEntityId, "actual_finish", "2026-01-01T12:00:00Z")),
                null
        ));

        assertThat(detail.batch().eligibleLineCount()).isZero();
        assertThat(detail.batch().ineligibleLineCount()).isEqualTo(1);
        assertThat(detail.lines().getFirst().leafTask()).isFalse();
        assertThat(detail.lines().getFirst().exportEligible()).isFalse();
    }

    @Test
    void keepsUnapprovedSourceLineIneligible() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());

        ExportPreviewDetail detail = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.awaitingReviewSourceEntityId, "actual_start", "2026-01-01T08:00:00Z")),
                null
        ));

        assertThat(detail.batch().eligibleLineCount()).isZero();
        assertThat(detail.lines().getFirst().approvalState()).isEqualTo(ApprovalState.AWAITING_REVIEW);
        assertThat(detail.lines().getFirst().exportEligible()).isFalse();
    }

    @Test
    void returnsStoredPreviewById() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "physical_percent_complete", "75")),
                null
        ));

        ExportPreviewDetail detail = service.getPreview(projectId, created.batch().id());

        assertThat(detail.batch().id()).isEqualTo(created.batch().id());
        assertThat(detail.lines()).hasSize(1);
        assertThat(detail.message()).contains("Export preview only.");
        assertThat(audit.events()).hasSize(1);
    }

    @Test
    void approvesDraftPreviewAndRecordsAuditEvent() {
        UUID projectId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));

        ExportPreviewDetail detail = service.approveBatch(
                projectId,
                created.batch().id(),
                new ExportBatchDecisionRequest(reviewerId, "Synthetic approval", Map.of("review", "local"))
        );
        AuditEventCreateRequest event = audit.events().getLast();

        assertThat(detail.batch().status()).isEqualTo(ExportBatchState.APPROVED);
        assertThat(detail.batch().approvedByUserId()).isEqualTo(reviewerId);
        assertThat(detail.batch().approvedAt()).isNotNull();
        assertThat(detail.message()).contains("No file was generated");
        assertThat(event.eventType()).isEqualTo(AuditEventTypes.EXPORT_BATCH_APPROVED);
        assertThat(event.oldValueSummary()).containsEntry("status", "draft_preview");
        assertThat(event.newValueSummary()).containsEntry("status", "approved");
        assertThat(event.metadata())
                .containsEntry("eligibleLineCount", 1)
                .containsEntry("artifactGenerated", false)
                .containsEntry("projectWriteBack", false);
    }

    @Test
    void rejectsDraftPreviewAndRecordsAuditEvent() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.summaryTaskId, repository.approvedSourceEntityId, "actual_finish", "2026-01-01T12:00:00Z")),
                null
        ));

        ExportPreviewDetail detail = service.rejectBatch(
                projectId,
                created.batch().id(),
                new ExportBatchDecisionRequest(null, "Synthetic rejection", null)
        );
        AuditEventCreateRequest event = audit.events().getLast();

        assertThat(detail.batch().status()).isEqualTo(ExportBatchState.REJECTED);
        assertThat(detail.message()).contains("No MSPDI/XML artifact was generated");
        assertThat(event.eventType()).isEqualTo(AuditEventTypes.EXPORT_BATCH_REJECTED);
        assertThat(event.oldValueSummary()).containsEntry("status", "draft_preview");
        assertThat(event.newValueSummary()).containsEntry("status", "rejected");
        assertThat(event.metadata())
                .containsEntry("artifactGenerated", false)
                .containsEntry("projectWriteBack", false);
    }

    @Test
    void marksApprovedBatchGeneratedFromArtifactMetadataOnly() {
        UUID projectId = UUID.randomUUID();
        UUID generatedByUserId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "physical_percent_complete", "75")),
                null
        ));
        ExportPreviewDetail approved = service.approveBatch(projectId, created.batch().id(), null);

        ExportPreviewDetail detail = service.markGenerated(projectId, approved.batch().id(), new ExportBatchGeneratedRequest(
                "object://synthetic/export-batches/export-1.mspdi.xml",
                "sha256:synthetic",
                generatedByUserId,
                "Synthetic worker artifact recorded",
                Map.of("source", "worker-spike")
        ));
        AuditEventCreateRequest event = audit.events().getLast();

        assertThat(detail.batch().status()).isEqualTo(ExportBatchState.GENERATED);
        assertThat(detail.batch().generatedByUserId()).isEqualTo(generatedByUserId);
        assertThat(detail.batch().generatedAt()).isNotNull();
        assertThat(detail.batch().exportFileUri()).isEqualTo("object://synthetic/export-batches/export-1.mspdi.xml");
        assertThat(detail.batch().exportFileHash()).isEqualTo("sha256:synthetic");
        assertThat(detail.message()).contains("No Microsoft Project write-back was run");
        assertThat(event.eventType()).isEqualTo(AuditEventTypes.EXPORT_FILE_GENERATED);
        assertThat(event.oldValueSummary()).containsEntry("status", "approved");
        assertThat(event.newValueSummary())
                .containsEntry("status", "generated")
                .containsEntry("exportFileUri", "object://synthetic/export-batches/export-1.mspdi.xml")
                .containsEntry("exportFileHash", "sha256:synthetic");
        assertThat(event.metadata())
                .containsEntry("artifactGenerated", true)
                .containsEntry("projectWriteBack", false);
    }

    @Test
    void rejectsApprovalWhenNoLinesAreEligible() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.summaryTaskId, repository.approvedSourceEntityId, "actual_finish", "2026-01-01T12:00:00Z")),
                null
        ));

        assertThatThrownBy(() -> service.approveBatch(projectId, created.batch().id(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Only export batches with at least one eligible line can be approved.");
        assertThat(audit.events()).hasSize(1);
    }

    @Test
    void rejectsGenerationBeforeApproval() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));

        assertThatThrownBy(() -> service.markGenerated(projectId, created.batch().id(), new ExportBatchGeneratedRequest(
                "object://synthetic/export-batches/export-1.mspdi.xml",
                "sha256:synthetic",
                null,
                null,
                null
        )))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Only approved export batches can be marked generated.");
        assertThat(audit.events()).hasSize(1);
    }

    @Test
    void rejectsUnknownImportedTaskForPreviewLine() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);

        assertThatThrownBy(() -> service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(UUID.randomUUID(), repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        )))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessageContaining("Imported task not found for export preview.");
        assertThat(audit.events()).isEmpty();
    }

    @Test
    void rejectsPreviewForNonAcceptedSnapshot() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        repository.acceptedSnapshot = false;
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);

        assertThatThrownBy(() -> service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        )))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Export preview requires an accepted project snapshot.");
        assertThat(audit.events()).isEmpty();
    }

    private ExportPreviewLineCreateRequest line(
            UUID importedTaskId,
            UUID sourceEntityId,
            String fieldName,
            String newValue
    ) {
        return new ExportPreviewLineCreateRequest(
                importedTaskId,
                "task_update",
                sourceEntityId,
                fieldName,
                newValue,
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-01-01T07:00:00Z"),
                "Synthetic reason",
                null
        );
    }

    private static class FakeExportPreviewRepository implements ExportPreviewRepository {

        private final UUID projectId;
        private final UUID projectSnapshotId = UUID.randomUUID();
        private final UUID exportBatchId = UUID.randomUUID();
        private final UUID leafTaskId = UUID.randomUUID();
        private final UUID summaryTaskId = UUID.randomUUID();
        private final UUID approvedSourceEntityId = UUID.randomUUID();
        private final UUID awaitingReviewSourceEntityId = UUID.randomUUID();
        private final Map<UUID, ExportPreviewTaskContext> tasks = new HashMap<>();
        private final List<ExportPreviewLineRecord> lines = new ArrayList<>();
        private ExportBatchState status = ExportBatchState.DRAFT_PREVIEW;
        private OffsetDateTime approvedAt;
        private UUID approvedByUserId;
        private OffsetDateTime generatedAt;
        private UUID generatedByUserId;
        private String exportFileUri;
        private String exportFileHash;
        private boolean acceptedSnapshot = true;
        private UUID createBatchProjectId;
        private UUID createBatchProjectSnapshotId;

        private FakeExportPreviewRepository(UUID projectId) {
            this.projectId = projectId;
            tasks.put(leafTaskId, new ExportPreviewTaskContext(
                    leafTaskId,
                    projectId,
                    projectSnapshotId,
                    "SYN-TASK-1",
                    "1",
                    "Synthetic Task A1",
                    false,
                    new BigDecimal("25.00"),
                    new BigDecimal("40.00"),
                    null,
                    null
            ));
            tasks.put(summaryTaskId, new ExportPreviewTaskContext(
                    summaryTaskId,
                    projectId,
                    projectSnapshotId,
                    "SYN-SUMMARY-1",
                    "10",
                    "Synthetic Summary",
                    true,
                    new BigDecimal("10.00"),
                    null,
                    null,
                    null
            ));
        }

        @Override
        public ExportPreviewBatchRecord createDraftPreview(
                UUID projectId,
                UUID projectSnapshotId,
                Map<String, Object> metadata
        ) {
            if (!acceptedSnapshot) {
                throw new IllegalArgumentException("Export preview requires an accepted project snapshot.");
            }
            createBatchProjectId = projectId;
            createBatchProjectSnapshotId = projectSnapshotId;
            return batch();
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
            if (status != ExportBatchState.DRAFT_PREVIEW) {
                return Optional.empty();
            }
            status = ExportBatchState.APPROVED;
            approvedAt = OffsetDateTime.parse("2026-01-01T01:00:00Z");
            this.approvedByUserId = approvedByUserId;
            return findBatch(projectId, exportBatchId);
        }

        @Override
        public Optional<ExportPreviewBatchRecord> rejectBatch(
                UUID projectId,
                UUID exportBatchId,
                Map<String, Object> metadata
        ) {
            if (status != ExportBatchState.DRAFT_PREVIEW) {
                return Optional.empty();
            }
            status = ExportBatchState.REJECTED;
            return findBatch(projectId, exportBatchId);
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
            return findBatch(projectId, exportBatchId);
        }

        @Override
        public Optional<ExportPreviewTaskContext> findTaskContext(
                UUID projectId,
                UUID projectSnapshotId,
                UUID importedTaskId
        ) {
            if (!this.projectId.equals(projectId) || !this.projectSnapshotId.equals(projectSnapshotId)) {
                return Optional.empty();
            }
            return Optional.ofNullable(tasks.get(importedTaskId));
        }

        @Override
        public Optional<ApprovalState> findLatestApprovalState(
                UUID projectId,
                String sourceEntityType,
                UUID sourceEntityId
        ) {
            if (approvedSourceEntityId.equals(sourceEntityId)) {
                return Optional.of(ApprovalState.APPROVED_FOR_EXPORT);
            }
            if (awaitingReviewSourceEntityId.equals(sourceEntityId)) {
                return Optional.of(ApprovalState.AWAITING_REVIEW);
            }
            return Optional.empty();
        }

        @Override
        public ExportPreviewLineRecord createLine(
                UUID projectId,
                UUID projectSnapshotId,
                UUID exportBatchId,
                ExportPreviewMaterializedLine line
        ) {
            ExportPreviewTaskContext task = tasks.get(line.importedTaskId());
            ExportPreviewLineRecord record = new ExportPreviewLineRecord(
                    UUID.randomUUID(),
                    exportBatchId,
                    projectId,
                    projectSnapshotId,
                    line.importedTaskId(),
                    task.externalUid(),
                    task.externalId(),
                    task.name(),
                    line.sourceEntityType(),
                    line.sourceEntityId(),
                    line.approvalState(),
                    line.fieldName(),
                    line.oldValue(),
                    line.newValue(),
                    line.sourceActorUserId(),
                    line.sourceTimestamp(),
                    line.reason(),
                    line.leafTask(),
                    line.exportEligible()
            );
            lines.add(record);
            return record;
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
                    approvedAt,
                    approvedByUserId,
                    generatedAt,
                    generatedByUserId,
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
