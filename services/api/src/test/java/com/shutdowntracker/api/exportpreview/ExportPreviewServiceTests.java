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
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ExportPreviewServiceTests {

    private static final Map<UUID, CandidateSpec> CANDIDATE_SPECS = new HashMap<>();

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
        assertThat(detail.lines().getFirst().oldValue()).isEqualTo("25");
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
    void rejectsUnapprovedSourceAsAnAuthoritativeCandidate() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());

        assertThatThrownBy(() -> service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.awaitingReviewSourceEntityId,
                        "actual_start", "2026-01-01T08:00:00Z")),
                null
        )))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("no longer matches")
                .hasMessageContaining("fresh export preview");
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
    void rejectsDifferentCandidatesForTheSameTaskAndFieldBeforeCreatingBatch() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());

        assertThatThrownBy(() -> service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(
                        line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50"),
                        line(repository.leafTaskId, repository.physicalSourceEntityId, "percent_complete", "50")
                ),
                null
        )))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Duplicate authoritative candidates")
                .hasMessageContaining("fresh export preview");
        assertThat(repository.createBatchProjectId).isNull();
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
    void mapsDatabaseIntegrityFailureDuringApprovalToFreshPreviewConflict() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));
        repository.failApprovalWithIntegrityViolation = true;

        assertThatThrownBy(() -> service.approveBatch(projectId, created.batch().id(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Export batch approval")
                .hasMessageContaining("stale candidate or accepted-baseline authority")
                .hasMessageContaining("fresh export preview");
        assertThat(repository.status).isEqualTo(ExportBatchState.DRAFT_PREVIEW);
        assertThat(audit.events()).hasSize(1);
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
        repository.integrityPolicyVersion = 3;

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
                .hasMessageContaining("no longer matches")
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
                .hasMessageContaining("no longer matches")
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
                .hasMessageContaining("no longer matches")
                .hasMessageContaining("fresh export preview");
        assertThat(repository.status).isEqualTo(ExportBatchState.DRAFT_PREVIEW);
    }

    @Test
    void rejectsApprovalForFrozenPolicyOneDraftBatch() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        repository.integrityPolicyVersion = 1;
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());
        ExportPreviewDetail frozen = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));

        assertThat(frozen.batch().integrityPolicyVersion()).isEqualTo(1);
        assertThatThrownBy(() -> service.approveBatch(projectId, frozen.batch().id(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("unsupported integrity policy")
                .hasMessageContaining("fresh export preview");
        assertThat(repository.status).isEqualTo(ExportBatchState.DRAFT_PREVIEW);
    }

    @Test
    void rejectsApprovalWhenCandidateBindingPolicyIsNotCurrent() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));
        ExportPreviewLineRecord line = created.lines().getFirst();
        repository.approvalCandidates.put(
                repository.approvedSourceEntityId,
                List.of(new ExportPreviewApprovalRecord(
                        line.sourceApprovalRecordId(),
                        ApprovalState.APPROVED_FOR_EXPORT,
                        line.authoritativeExportCandidateId(),
                        1
                ))
        );

        assertThatThrownBy(() -> service.approveBatch(projectId, created.batch().id(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("no longer matches")
                .hasMessageContaining("fresh export preview");
    }

    @Test
    void rejectsApprovalWhenAcceptedSnapshotBecomesStale() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));
        repository.acceptedSnapshot = false;

        assertThatThrownBy(() -> service.approveBatch(projectId, created.batch().id(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("no longer an accepted baseline")
                .hasMessageContaining("fresh export preview");
    }

    @Test
    void rejectsGenerationWhenAcceptedSnapshotBecomesStale() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));
        ExportPreviewDetail approved = service.approveBatch(projectId, created.batch().id(), null);
        repository.acceptedSnapshot = false;

        assertThatThrownBy(() -> service.markGenerated(
                projectId,
                approved.batch().id(),
                new ExportBatchGeneratedRequest(
                        "object://synthetic/export-batches/stale.mspdi.xml",
                        "sha256:stale",
                        UUID.randomUUID(),
                        "Must remain blocked",
                        null
                )
        ))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("no longer an accepted baseline")
                .hasMessageContaining("fresh export preview");
        assertThat(repository.status).isEqualTo(ExportBatchState.APPROVED);
    }

    @Test
    void rejectsImportedTaskUidDrift() {
        assertTaskDriftBlocksApproval(repository -> repository.replaceLeafTask(
                "CHANGED-UID", "1", "Synthetic Task A1", false, new BigDecimal("25")
        ));
    }

    @Test
    void rejectsImportedTaskIdDrift() {
        assertTaskDriftBlocksApproval(repository -> repository.replaceLeafTask(
                "SYN-TASK-1", "CHANGED-ID", "Synthetic Task A1", false, new BigDecimal("25")
        ));
    }

    @Test
    void rejectsImportedTaskNameDrift() {
        assertTaskDriftBlocksApproval(repository -> repository.replaceLeafTask(
                "SYN-TASK-1", "1", "Changed task name", false, new BigDecimal("25")
        ));
    }

    @Test
    void rejectsImportedTaskLeafStatusDrift() {
        assertTaskDriftBlocksApproval(repository -> repository.replaceLeafTask(
                "SYN-TASK-1", "1", "Synthetic Task A1", true, new BigDecimal("25")
        ));
    }

    @Test
    void rejectsImportedTaskOldValueDrift() {
        assertTaskDriftBlocksApproval(repository -> repository.replaceLeafTask(
                "SYN-TASK-1", "1", "Synthetic Task A1", false, new BigDecimal("26")
        ));
    }

    @Test
    void rejectsReusingCandidateWithDifferentTask() {
        assertTamperedLineBlocksApproval(repository ->
                repository.tamperFirstLine(UUID.randomUUID(), null, null, null));
    }

    @Test
    void rejectsReusingCandidateWithDifferentField() {
        assertTamperedLineBlocksApproval(repository ->
                repository.tamperFirstLine(null, "actual_finish", null, null));
    }

    @Test
    void rejectsReusingCandidateWithDifferentValue() {
        assertTamperedLineBlocksApproval(repository ->
                repository.tamperFirstLine(null, null, "99", null));
    }

    @Test
    void rejectsCandidateSourceHashMismatch() {
        assertTamperedLineBlocksApproval(repository ->
                repository.tamperFirstLine(null, null, null, "c".repeat(64)));
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
    void mapsDatabaseIntegrityFailureDuringGenerationToFreshPreviewConflict() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "75")),
                null
        ));
        ExportPreviewDetail approved = service.approveBatch(projectId, created.batch().id(), null);
        repository.failGenerationWithIntegrityViolation = true;

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
                .hasMessageContaining("Export batch generation")
                .hasMessageContaining("stale candidate or accepted-baseline authority")
                .hasMessageContaining("fresh export preview");
        assertThat(repository.status).isEqualTo(ExportBatchState.APPROVED);
        assertThat(audit.events()).hasSize(2);
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
                .hasMessageContaining("no longer matches")
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
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("no longer matches")
                .hasMessageContaining("fresh export preview");
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
                .hasMessageContaining("no longer an accepted baseline")
                .hasMessageContaining("fresh export preview");
        assertThat(audit.events()).isEmpty();
    }

    @Test
    void mapsDatabaseIntegrityFailureWhileSealingToFreshPreviewConflict() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        repository.failSealingWithIntegrityViolation = true;
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);

        assertThatThrownBy(() -> service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        )))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Export preview sealing")
                .hasMessageContaining("stale candidate or accepted-baseline authority")
                .hasMessageContaining("fresh export preview");
        assertThat(repository.status).isEqualTo(ExportBatchState.DRAFT_PREVIEW);
        assertThat(audit.events()).isEmpty();
    }

    @Test
    void returnsFreshPreviewConflictWhenCandidateChangesBeforeLineInsertion() {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        repository.failLineCreation = true;
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ExportPreviewService service = new ExportPreviewService(repository, audit);

        assertThatThrownBy(() -> service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        )))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("authority changed")
                .hasMessageContaining("fresh export preview");
        assertThat(audit.events()).isEmpty();
    }

    private void assertTaskDriftBlocksApproval(Consumer<FakeExportPreviewRepository> drift) {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));
        drift.accept(repository);

        assertThatThrownBy(() -> service.approveBatch(projectId, created.batch().id(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("no longer matches")
                .hasMessageContaining("fresh export preview");
        assertThat(repository.status).isEqualTo(ExportBatchState.DRAFT_PREVIEW);
    }

    private void assertTamperedLineBlocksApproval(Consumer<FakeExportPreviewRepository> tamper) {
        UUID projectId = UUID.randomUUID();
        FakeExportPreviewRepository repository = new FakeExportPreviewRepository(projectId);
        ExportPreviewService service = new ExportPreviewService(repository, new CapturingAuditEventRecorder());
        ExportPreviewDetail created = service.createPreview(projectId, new ExportPreviewCreateRequest(
                repository.projectSnapshotId,
                List.of(line(repository.leafTaskId, repository.approvedSourceEntityId, "percent_complete", "50")),
                null
        ));
        tamper.accept(repository);

        assertThatThrownBy(() -> service.approveBatch(projectId, created.batch().id(), null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("fresh export preview");
        assertThat(repository.status).isEqualTo(ExportBatchState.DRAFT_PREVIEW);
    }

    private ExportPreviewLineCreateRequest line(
            UUID importedTaskId,
            UUID sourceEntityId,
            String fieldName,
            String newValue
    ) {
        UUID candidateId = UUID.randomUUID();
        CANDIDATE_SPECS.put(candidateId, new CandidateSpec(importedTaskId, sourceEntityId, fieldName, newValue));
        return new ExportPreviewLineCreateRequest(candidateId);
    }

    private record CandidateSpec(UUID importedTaskId, UUID sourceEntityId, String fieldName, String newValue) {
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
        private final Map<UUID, ExportCandidateRecord> candidates = new HashMap<>();
        private final Map<UUID, UUID> authoritativeCandidateBySource = new HashMap<>();
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
        private boolean failSealingWithIntegrityViolation;
        private boolean failApprovalWithIntegrityViolation;
        private boolean failGenerationWithIntegrityViolation;
        private boolean failLineCreation;
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
            if (failSealingWithIntegrityViolation) {
                throw new DataIntegrityViolationException("Synthetic stale candidate during sealing.");
            }
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
            if (failApprovalWithIntegrityViolation) {
                throw new DataIntegrityViolationException("Synthetic stale candidate during approval.");
            }
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
            if (failGenerationWithIntegrityViolation) {
                throw new DataIntegrityViolationException("Synthetic stale candidate during generation.");
            }
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
        public boolean lockAcceptedSnapshotForIntegrityValidation(UUID projectId, UUID projectSnapshotId) {
            return acceptedSnapshot
                    && this.projectId.equals(projectId)
                    && this.projectSnapshotId.equals(projectSnapshotId);
        }

        @Override
        public Optional<ExportCandidateRecord> findAuthoritativeCandidate(
                UUID projectId,
                UUID authoritativeExportCandidateId
        ) {
            if (!this.projectId.equals(projectId)) {
                return Optional.empty();
            }
            ExportCandidateRecord existing = candidates.get(authoritativeExportCandidateId);
            if (existing != null) {
                return Optional.of(existing);
            }
            CandidateSpec spec = CANDIDATE_SPECS.get(authoritativeExportCandidateId);
            if (spec == null) {
                return Optional.empty();
            }
            ExportPreviewTaskContext task = tasks.get(spec.importedTaskId());
            List<ExportPreviewApprovalRecord> approvals = approvalCandidates.getOrDefault(
                    spec.sourceEntityId(),
                    List.of()
            );
            ExportPreviewApprovalRecord approval = approvals.isEmpty()
                    ? new ExportPreviewApprovalRecord(UUID.randomUUID(), ApprovalState.APPROVED_FOR_EXPORT)
                    : approvals.getFirst();
            ExportPreviewField field = ExportPreviewField.fromFieldName(spec.fieldName());
            ExportCandidateRecord candidate = new ExportCandidateRecord(
                    authoritativeExportCandidateId,
                    approval.id(),
                    ExportIntegrityPolicy.CURRENT_VERSION,
                    this.projectId,
                    projectSnapshotId,
                    spec.importedTaskId(),
                    "task_update",
                    spec.sourceEntityId(),
                    approval.approvalState(),
                    field.fieldName(),
                    task == null ? null : field.oldValue(task),
                    field.normalizeValue(spec.newValue()),
                    "a".repeat(64),
                    task == null ? "MISSING-TASK" : task.externalUid(),
                    task == null ? "missing" : task.externalId(),
                    task == null ? "Missing task" : task.name(),
                    task == null || task.leafTask(),
                    UUID.randomUUID(),
                    OffsetDateTime.parse("2026-01-01T07:00:00Z"),
                    "Synthetic reason"
            );
            candidates.put(authoritativeExportCandidateId, candidate);
            authoritativeCandidateBySource.put(spec.sourceEntityId(), authoritativeExportCandidateId);
            return Optional.of(candidate);
        }

        @Override
        public List<ExportPreviewApprovalRecord> findCurrentApprovalCandidates(
                UUID projectId,
                String sourceEntityType,
                UUID sourceEntityId
        ) {
            UUID candidateId = authoritativeCandidateBySource.get(sourceEntityId);
            ExportCandidateRecord candidate = candidateId == null ? null : candidates.get(candidateId);
            return approvalCandidates.getOrDefault(sourceEntityId, List.of()).stream()
                    .map(approval -> new ExportPreviewApprovalRecord(
                            approval.id(),
                            approval.approvalState(),
                            approval.authoritativeExportCandidateId() != null
                                    ? approval.authoritativeExportCandidateId()
                                    : candidate != null && candidate.approvalRecordId().equals(approval.id())
                                    ? candidate.id()
                                    : null,
                            approval.candidateBindingPolicyVersion() != null
                                    ? approval.candidateBindingPolicyVersion()
                                    : candidate != null && candidate.approvalRecordId().equals(approval.id())
                                    ? Integer.valueOf(ExportIntegrityPolicy.CURRENT_VERSION)
                                    : null
                    ))
                    .toList();
        }

        @Override
        public ExportPreviewLineRecord createLine(
                UUID projectId,
                UUID projectSnapshotId,
                UUID exportBatchId,
                UUID authoritativeExportCandidateId
        ) {
            if (failLineCreation) {
                throw new IllegalArgumentException("Candidate changed before line insertion.");
            }
            ExportCandidateRecord candidate = findAuthoritativeCandidate(projectId, authoritativeExportCandidateId)
                    .orElseThrow();
            boolean exportEligible = candidate.approvalState() == ApprovalState.APPROVED_FOR_EXPORT
                    && candidate.capturedLeafTask()
                    && ExportPreviewField.fromFieldName(candidate.fieldName()).mvpExportAuthorized();
            ExportPreviewLineRecord record = new ExportPreviewLineRecord(
                    UUID.randomUUID(),
                    exportBatchId,
                    projectId,
                    projectSnapshotId,
                    candidate.importedTaskId(),
                    candidate.capturedTaskExternalUid(),
                    candidate.capturedTaskExternalId(),
                    candidate.capturedTaskName(),
                    candidate.sourceEntityType(),
                    candidate.sourceEntityId(),
                    candidate.approvalState(),
                    candidate.approvalRecordId(),
                    candidate.fieldName(),
                    candidate.normalizedOldValue(),
                    candidate.normalizedNewValue(),
                    candidate.sourceActorUserId(),
                    candidate.sourceTimestamp(),
                    candidate.reason(),
                    candidate.capturedLeafTask(),
                    exportEligible,
                    integrityPolicyVersion,
                    candidate.id(),
                    candidate.sourceEventOrPayloadHash()
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

        private void replaceLeafTask(
                String externalUid,
                String externalId,
                String name,
                boolean summary,
                BigDecimal percentComplete
        ) {
            ExportPreviewTaskContext current = tasks.get(leafTaskId);
            tasks.put(leafTaskId, new ExportPreviewTaskContext(
                    current.id(),
                    current.projectId(),
                    current.projectSnapshotId(),
                    externalUid,
                    externalId,
                    name,
                    summary,
                    percentComplete,
                    current.physicalPercentComplete(),
                    current.actualStart(),
                    current.actualFinish()
            ));
        }

        private void tamperFirstLine(UUID importedTaskId, String fieldName, String newValue, String sourceHash) {
            ExportPreviewLineRecord current = lines.getFirst();
            lines.set(0, new ExportPreviewLineRecord(
                    current.id(),
                    current.exportBatchId(),
                    current.projectId(),
                    current.projectSnapshotId(),
                    importedTaskId == null ? current.importedTaskId() : importedTaskId,
                    current.importedTaskExternalUid(),
                    current.importedTaskExternalId(),
                    current.importedTaskName(),
                    current.sourceEntityType(),
                    current.sourceEntityId(),
                    current.approvalState(),
                    current.sourceApprovalRecordId(),
                    fieldName == null ? current.fieldName() : fieldName,
                    current.oldValue(),
                    newValue == null ? current.newValue() : newValue,
                    current.sourceActorUserId(),
                    current.sourceTimestamp(),
                    current.reason(),
                    current.leafTask(),
                    current.exportEligible(),
                    current.integrityPolicyVersion(),
                    current.authoritativeExportCandidateId(),
                    sourceHash == null ? current.capturedSourceEventOrPayloadHash() : sourceHash
            ));
        }
    }
}
