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
        assertThat(detail.batch().integrityPolicyVersion()).isEqualTo(ExportIntegrityPolicy.CURRENT_VERSION);
        assertThat(detail.batch().lineSetSealed()).isTrue();
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
    void keepsApprovedPhysicalPercentCompleteInternalButIneligible() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());

        ExportPreviewDetail detail = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(
                        repository.leafTaskId,
                        repository.approvedSourceEntityId,
                        "physical_percent_complete",
                        "75"
                )),
                null
        ));

        assertThat(detail.lines().getFirst().approvalState()).isEqualTo(ApprovalState.APPROVED_FOR_EXPORT);
        assertThat(detail.lines().getFirst().sourceApprovalRecordId())
                .isEqualTo(repository.approvedApprovalRecordId);
        assertThat(detail.lines().getFirst().integrityPolicyVersion())
                .isEqualTo(ExportIntegrityPolicy.CURRENT_VERSION);
        assertThat(detail.lines().getFirst().leafTask()).isTrue();
        assertThat(detail.lines().getFirst().exportEligible()).isFalse();
        assertThat(detail.batch().eligibleLineCount()).isZero();
    }

    @Test
    void returnsStoredPreviewById() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "75")),
                null
        ));

        ExportPreviewDetail detail = service.getPreview(projectId, created.batch().id());

        assertThat(detail.batch().id()).isEqualTo(created.batch().id());
        assertThat(detail.lines()).hasSize(1);
        assertThat(detail.message()).contains("Export preview only.");
        assertThat(audit.events()).hasSize(1);
    }

    @Test
    void rejectsPreviewWhenLineMembershipCannotBeSealed() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        repository.sealSucceeds = false;
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);

        assertThatThrownBy(() -> service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        )))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("line membership could not be sealed")
                .hasMessageContaining("fresh export preview");
        assertThat(audit.events()).isEmpty();
    }

    @Test
    void rejectsApprovalForLegacyDraftBatchWithFreshPreviewConflict() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        repository.integrityPolicyVersion = null;
        repository.lineSetSealed = null;
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());
        ExportPreviewDetail legacy = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));

        assertThat(legacy.batch().integrityPolicyVersion()).isNull();
        assertThat(legacy.lines().getFirst().integrityPolicyVersion()).isNull();
        assertThatThrownBy(() -> service.approveBatch(projectId, legacy.batch().id(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("read-only history")
                .hasMessageContaining("fresh export preview");
        assertThat(repository.status).isEqualTo(ExportBatchState.DRAFT_PREVIEW);
    }

    @Test
    void rejectsGenerationAndHandoffForLegacyApprovedBatchWithFreshPreviewConflict() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        repository.integrityPolicyVersion = null;
        repository.lineSetSealed = null;
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());
        ExportPreviewDetail legacy = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));
        repository.status = ExportBatchState.APPROVED;

        assertThatThrownBy(() -> service.getApprovedPreviewForArtifactGeneration(projectId, legacy.batch().id()))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("read-only history")
                .hasMessageContaining("fresh export preview");
        assertThatThrownBy(() -> service.markGenerated(
                projectId,
                legacy.batch().id(),
                new ExportBatchGeneratedRequest(
                        "object://synthetic/export-batches/legacy.mspdi.xml",
                        "sha256:legacy",
                        UUID.randomUUID(),
                        "Legacy generation must remain blocked",
                        null
                )
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("read-only history")
                .hasMessageContaining("fresh export preview");
        assertThat(repository.status).isEqualTo(ExportBatchState.APPROVED);
    }

    @Test
    void keepsAllLegacyTerminalHistoryReadable() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        repository.integrityPolicyVersion = null;
        repository.lineSetSealed = null;
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());
        ExportPreviewDetail legacy = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(
                        repository.leafTaskId,
                        repository.approvedSourceEntityId,
                        "physical_percent_complete",
                        "75"
                )),
                null
        ));

        for (ExportBatchState historicalState : List.of(
                ExportBatchState.GENERATED,
                ExportBatchState.OPENED_IN_MICROSOFT_PROJECT,
                ExportBatchState.VERIFIED,
                ExportBatchState.REJECTED,
                ExportBatchState.SUPERSEDED
        )) {
            repository.status = historicalState;
            ExportPreviewDetail detail = service.getPreview(projectId, legacy.batch().id());

            assertThat(detail.batch().status()).isEqualTo(historicalState);
            assertThat(detail.batch().integrityPolicyVersion()).isNull();
            assertThat(detail.batch().lineSetSealed()).isNull();
            assertThat(detail.lines()).hasSize(1);
            assertThat(detail.lines().getFirst().fieldName()).isEqualTo("physical_percent_complete");
            assertThat(detail.lines().getFirst().integrityPolicyVersion()).isNull();
        }
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
        assertThat(repository.integrityLockCount).isEqualTo(1);
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
    void rejectsUnsealedCurrentPolicyBatch() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));
        repository.lineSetSealed = false;

        assertThatThrownBy(() -> service.approveBatch(projectId, created.batch().id(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("sealed line set")
                .hasMessageContaining("fresh export preview");
        assertThat(repository.status).isEqualTo(ExportBatchState.DRAFT_PREVIEW);
    }

    @Test
    void rejectsUnknownIntegrityPolicyVersionWithoutTreatingItAsLegacy() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));
        repository.integrityPolicyVersion = 2;

        assertThatThrownBy(() -> service.approveBatch(projectId, created.batch().id(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getMessage())
                            .contains("unsupported integrity policy", "current policy")
                            .doesNotContain("predates");
                });
        assertThat(repository.status).isEqualTo(ExportBatchState.DRAFT_PREVIEW);
    }

    @Test
    void rejectsApprovalWhenSourceWasRejectedAfterPreviewCreation() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));
        repository.replaceCurrentApproval(repository.approvedSourceEntityId, ApprovalState.REJECTED);

        assertThatThrownBy(() -> service.approveBatch(projectId, created.batch().id(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("authority changed")
                .hasMessageContaining("fresh export preview");
        assertThat(repository.status).isEqualTo(ExportBatchState.DRAFT_PREVIEW);
    }

    @Test
    void rejectsApprovalWhenOriginallyIneligibleLineApprovalChanges() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(
                        line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50"),
                        line(repository.leafTaskId, repository.physicalSourceEntityId, "physical_percent_complete", "75")
                ),
                null
        ));
        repository.replaceCurrentApproval(repository.physicalSourceEntityId, ApprovalState.REJECTED);

        assertThat(created.batch().eligibleLineCount()).isEqualTo(1);
        assertThat(created.batch().ineligibleLineCount()).isEqualTo(1);
        assertThatThrownBy(() -> service.approveBatch(projectId, created.batch().id(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("authority changed")
                .hasMessageContaining("fresh export preview");
        assertThat(repository.status).isEqualTo(ExportBatchState.DRAFT_PREVIEW);
    }

    @Test
    void rejectsApprovalWhenCurrentApprovalIdentityChangesWithoutStateChange() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));
        UUID capturedApprovalId = created.lines().getFirst().sourceApprovalRecordId();
        UUID replacementApprovalId = repository.replaceCurrentApproval(
                repository.approvedSourceEntityId,
                ApprovalState.APPROVED_FOR_EXPORT
        );

        assertThat(replacementApprovalId).isNotEqualTo(capturedApprovalId);
        assertThatThrownBy(() -> service.approveBatch(projectId, created.batch().id(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("authority changed")
                .hasMessageContaining("fresh export preview");
        assertThat(repository.status).isEqualTo(ExportBatchState.DRAFT_PREVIEW);
    }

    @Test
    void rejectsPreviewWhenCurrentApprovalIsMissing() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        repository.approvalCandidates.remove(repository.approvedSourceEntityId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());

        assertThatThrownBy(() -> service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        )))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("no current approval record")
                .hasMessageContaining("fresh export preview");
    }

    @Test
    void rejectsPreviewWhenLegacyApprovalAuthorityIsAmbiguous() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        repository.approvalCandidates.put(
                repository.approvedSourceEntityId,
                List.of(
                        new ExportPreviewApprovalRecord(UUID.randomUUID(), ApprovalState.APPROVED_FOR_EXPORT),
                        new ExportPreviewApprovalRecord(UUID.randomUUID(), ApprovalState.REJECTED)
                )
        );
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());

        assertThatThrownBy(() -> service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        )))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("ambiguous legacy approval authority")
                .hasMessageContaining("new approval event");
    }

    @Test
    void capturesSingleResolvedCandidateForSameTransactionApprovalEvents() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        UUID resolvedApprovalId = UUID.randomUUID();
        repository.approvalCandidates.put(
                repository.approvedSourceEntityId,
                List.of(new ExportPreviewApprovalRecord(resolvedApprovalId, ApprovalState.APPROVED_FOR_EXPORT))
        );
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());

        ExportPreviewDetail detail = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));

        assertThat(detail.lines().getFirst().sourceApprovalRecordId()).isEqualTo(resolvedApprovalId);
        assertThat(detail.lines().getFirst().approvalState()).isEqualTo(ApprovalState.APPROVED_FOR_EXPORT);
        assertThat(detail.lines().getFirst().exportEligible()).isTrue();
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
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "75")),
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
    void rejectsGeneratedMetadataWhenSourceWasSupersededAfterApproval() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));
        ExportPreviewDetail approved = service.approveBatch(projectId, created.batch().id(), null);
        repository.replaceCurrentApproval(repository.approvedSourceEntityId, ApprovalState.SUPERSEDED);

        assertThatThrownBy(() -> service.markGenerated(
                projectId,
                approved.batch().id(),
                new ExportBatchGeneratedRequest(
                        "object://synthetic/export-batches/export-1.mspdi.xml",
                        "sha256:synthetic",
                        UUID.randomUUID(),
                        "Synthetic worker artifact recorded",
                        null
                )
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("authority changed")
                .hasMessageContaining("fresh export preview");
        assertThat(repository.status).isEqualTo(ExportBatchState.APPROVED);
        assertThat(audit.events()).hasSize(2);
    }

    @Test
    void marksGeneratedBatchOpenedInMicrosoftProjectForManualVerification() {
        UUID projectId = UUID.randomUUID();
        UUID openedByUserId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "75")),
                null
        ));
        ExportPreviewDetail approved = service.approveBatch(projectId, created.batch().id(), null);
        ExportPreviewDetail generated = service.markGenerated(projectId, approved.batch().id(), new ExportBatchGeneratedRequest(
                "object://synthetic/export-batches/export-1.mspdi.xml",
                "sha256:synthetic",
                UUID.randomUUID(),
                "Synthetic worker artifact recorded",
                null
        ));

        ExportPreviewDetail detail = service.markOpenedInMicrosoftProject(
                projectId,
                generated.batch().id(),
                new ExportBatchProjectOpenRequest(
                        openedByUserId,
                        "Synthetic Microsoft Project reopen",
                        Map.of("review", "manual-smoke")
                )
        );
        AuditEventCreateRequest event = audit.events().getLast();

        assertThat(detail.batch().status()).isEqualTo(ExportBatchState.OPENED_IN_MICROSOFT_PROJECT);
        assertThat(detail.batch().exportFileUri()).isEqualTo("object://synthetic/export-batches/export-1.mspdi.xml");
        assertThat(detail.batch().exportFileHash()).isEqualTo("sha256:synthetic");
        assertThat(detail.batch().verifiedAt()).isNull();
        assertThat(detail.batch().verifiedByUserId()).isNull();
        assertThat(detail.message()).contains("for manual verification");
        assertThat(detail.message()).contains("No Microsoft Project write-back was run");
        assertThat(event.eventType()).isEqualTo(AuditEventTypes.EXPORT_FILE_OPENED_IN_MICROSOFT_PROJECT);
        assertThat(event.oldValueSummary()).containsEntry("status", "generated");
        assertThat(event.newValueSummary()).containsEntry("status", "opened_in_microsoft_project");
        assertThat(event.metadata())
                .containsEntry("artifactGenerated", true)
                .containsEntry("openedInMicrosoftProject", true)
                .containsEntry("artifactVerified", false)
                .containsEntry("projectWriteBack", false);
    }

    @Test
    void verifiesBatchAfterMicrosoftProjectOpen() {
        UUID projectId = UUID.randomUUID();
        UUID verifiedByUserId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));
        ExportPreviewDetail approved = service.approveBatch(projectId, created.batch().id(), null);
        ExportPreviewDetail generated = service.markGenerated(projectId, approved.batch().id(), new ExportBatchGeneratedRequest(
                "object://synthetic/export-batches/export-1.mspdi.xml",
                "sha256:synthetic",
                UUID.randomUUID(),
                "Synthetic worker artifact recorded",
                null
        ));
        ExportPreviewDetail opened = service.markOpenedInMicrosoftProject(
                projectId,
                generated.batch().id(),
                new ExportBatchProjectOpenRequest(UUID.randomUUID(), "Synthetic Microsoft Project reopen", null)
        );

        ExportPreviewDetail detail = service.verifyBatch(
                projectId,
                opened.batch().id(),
                new ExportBatchVerificationRequest(
                        verifiedByUserId,
                        "Synthetic manual verification complete",
                        Map.of("review", "manual-smoke")
                )
        );
        AuditEventCreateRequest event = audit.events().getLast();

        assertThat(detail.batch().status()).isEqualTo(ExportBatchState.VERIFIED);
        assertThat(detail.batch().verifiedByUserId()).isEqualTo(verifiedByUserId);
        assertThat(detail.batch().verifiedAt()).isNotNull();
        assertThat(detail.message()).contains("manually verified after Microsoft Project reopen");
        assertThat(detail.message()).contains("No Microsoft Project write-back was run");
        assertThat(event.eventType()).isEqualTo(AuditEventTypes.EXPORT_FILE_VERIFIED);
        assertThat(event.oldValueSummary()).containsEntry("status", "opened_in_microsoft_project");
        assertThat(event.newValueSummary())
                .containsEntry("status", "verified")
                .containsEntry("verifiedByUserId", verifiedByUserId.toString());
        assertThat(event.metadata())
                .containsEntry("artifactGenerated", true)
                .containsEntry("openedInMicrosoftProject", true)
                .containsEntry("artifactVerified", true)
                .containsEntry("projectWriteBack", false);
    }

    @Test
    void rejectsProjectOpenBeforeGeneration() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));

        assertThatThrownBy(() -> service.markOpenedInMicrosoftProject(
                projectId,
                created.batch().id(),
                new ExportBatchProjectOpenRequest(UUID.randomUUID(), "Synthetic Microsoft Project reopen", null)
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Only generated export batches can be marked opened in Microsoft Project.");
        assertThat(audit.events()).hasSize(1);
    }

    @Test
    void rejectsVerificationBeforeProjectOpen() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));
        ExportPreviewDetail approved = service.approveBatch(projectId, created.batch().id(), null);
        ExportPreviewDetail generated = service.markGenerated(projectId, approved.batch().id(), new ExportBatchGeneratedRequest(
                "object://synthetic/export-batches/export-1.mspdi.xml",
                "sha256:synthetic",
                UUID.randomUUID(),
                "Synthetic worker artifact recorded",
                null
        ));

        assertThatThrownBy(() -> service.verifyBatch(
                projectId,
                generated.batch().id(),
                new ExportBatchVerificationRequest(
                        UUID.randomUUID(),
                        "Synthetic manual verification complete",
                        null
                )
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Only export batches opened in Microsoft Project can be verified.");
        assertThat(audit.events()).hasSize(3);
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
        private final UUID physicalSourceEntityId = UUID.randomUUID();
        private final UUID approvedApprovalRecordId = UUID.randomUUID();
        private final UUID awaitingReviewApprovalRecordId = UUID.randomUUID();
        private final UUID physicalApprovalRecordId = UUID.randomUUID();
        private final Map<UUID, ExportPreviewTaskContext> tasks = new HashMap<>();
        private final Map<UUID, List<ExportPreviewApprovalRecord>> approvalCandidates = new HashMap<>();
        private final List<ExportPreviewLineRecord> lines = new ArrayList<>();
        private ExportBatchState status = ExportBatchState.DRAFT_PREVIEW;
        private OffsetDateTime approvedAt;
        private UUID approvedByUserId;
        private OffsetDateTime generatedAt;
        private UUID generatedByUserId;
        private OffsetDateTime verifiedAt;
        private UUID verifiedByUserId;
        private String exportFileUri;
        private String exportFileHash;
        private boolean acceptedSnapshot = true;
        private UUID createBatchProjectId;
        private UUID createBatchProjectSnapshotId;
        private Integer integrityPolicyVersion = ExportIntegrityPolicy.CURRENT_VERSION;
        private Boolean lineSetSealed = false;
        private boolean sealSucceeds = true;
        private int integrityLockCount;

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
            approvalCandidates.put(
                    approvedSourceEntityId,
                    List.of(new ExportPreviewApprovalRecord(
                            approvedApprovalRecordId,
                            ApprovalState.APPROVED_FOR_EXPORT
                    ))
            );
            approvalCandidates.put(
                    awaitingReviewSourceEntityId,
                    List.of(new ExportPreviewApprovalRecord(
                            awaitingReviewApprovalRecordId,
                            ApprovalState.AWAITING_REVIEW
                    ))
            );
            approvalCandidates.put(
                    physicalSourceEntityId,
                    List.of(new ExportPreviewApprovalRecord(
                            physicalApprovalRecordId,
                            ApprovalState.APPROVED_FOR_EXPORT
                    ))
            );
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
        public boolean sealDraftPreviewLineSet(UUID projectId, UUID exportBatchId) {
            if (!sealSucceeds) {
                return false;
            }
            if (integrityPolicyVersion == null) {
                return true;
            }
            if (status != ExportBatchState.DRAFT_PREVIEW || !Boolean.FALSE.equals(lineSetSealed)) {
                return false;
            }
            lineSetSealed = true;
            return true;
        }

        @Override
        public boolean lockBatchForIntegrityValidation(UUID projectId, UUID exportBatchId) {
            integrityLockCount++;
            return this.projectId.equals(projectId) && this.exportBatchId.equals(exportBatchId);
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
        public Optional<ExportPreviewBatchRecord> markBatchOpenedInMicrosoftProject(
                UUID projectId,
                UUID exportBatchId,
                Map<String, Object> metadata
        ) {
            if (status != ExportBatchState.GENERATED) {
                return Optional.empty();
            }
            status = ExportBatchState.OPENED_IN_MICROSOFT_PROJECT;
            return findBatch(projectId, exportBatchId);
        }

        @Override
        public Optional<ExportPreviewBatchRecord> markBatchVerified(
                UUID projectId,
                UUID exportBatchId,
                UUID verifiedByUserId,
                Map<String, Object> metadata
        ) {
            if (status != ExportBatchState.OPENED_IN_MICROSOFT_PROJECT) {
                return Optional.empty();
            }
            status = ExportBatchState.VERIFIED;
            verifiedAt = OffsetDateTime.parse("2026-01-01T03:00:00Z");
            this.verifiedByUserId = verifiedByUserId;
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
        public List<ExportPreviewApprovalRecord> findCurrentApprovalCandidates(
                UUID projectId,
                String sourceEntityType,
                UUID sourceEntityId
        ) {
            return approvalCandidates.getOrDefault(sourceEntityId, List.of());
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
                    line.sourceApprovalRecordId(),
                    line.fieldName(),
                    line.oldValue(),
                    line.newValue(),
                    line.sourceActorUserId(),
                    line.sourceTimestamp(),
                    line.reason(),
                    line.leafTask(),
                    line.exportEligible(),
                    integrityPolicyVersion
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
                    verifiedAt,
                    verifiedByUserId,
                    exportFileUri,
                    exportFileHash,
                    null,
                    lines.size(),
                    eligible,
                    lines.size() - eligible,
                    integrityPolicyVersion,
                    lineSetSealed
            );
        }

        private UUID replaceCurrentApproval(UUID sourceEntityId, ApprovalState approvalState) {
            UUID approvalRecordId = UUID.randomUUID();
            approvalCandidates.put(
                    sourceEntityId,
                    List.of(new ExportPreviewApprovalRecord(approvalRecordId, approvalState))
            );
            return approvalRecordId;
        }
    }
}
