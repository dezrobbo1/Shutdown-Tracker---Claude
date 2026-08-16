package com.shutdowntracker.api.importreview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventTypes;
import com.shutdowntracker.api.audit.CapturingAuditEventRecorder;
import com.shutdowntracker.api.importedproject.ImportedExtendedAttributeEntityType;
import com.shutdowntracker.api.importedproject.ProjectSnapshotStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.shutdowntracker.api.actor.StubActorConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ImportReviewServiceTests {

    @Test
    void returnsSnapshotDetailWithImportedEntitiesForReview() {
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        FakeImportReviewRepository repository = new FakeImportReviewRepository(projectId, snapshotId);
        ImportReviewService service = new ImportReviewService(repository, new CapturingAuditEventRecorder());

        ImportReviewSnapshotDetail detail = service.getSnapshot(projectId, snapshotId);

        assertThat(detail.snapshot().id()).isEqualTo(snapshotId);
        assertThat(detail.snapshot().taskCount()).isEqualTo(2);
        assertThat(detail.snapshot().summaryTaskCount()).isEqualTo(1);
        assertThat(detail.snapshot().leafTaskCount()).isEqualTo(1);
        assertThat(detail.tasks()).hasSize(2);
        assertThat(detail.tasks()).extracting(ImportReviewTaskRow::summary).containsExactly(true, false);
        assertThat(detail.resources()).hasSize(1);
        assertThat(detail.assignments()).hasSize(1);
        assertThat(detail.extendedAttributes()).hasSize(1);
    }

    @Test
    void acceptsOnlyParsedSnapshotsUsingExistingStatusValues() {
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        FakeImportReviewRepository repository = new FakeImportReviewRepository(projectId, snapshotId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ImportReviewService service = new ImportReviewService(repository, audit);

        ImportReviewDecisionResponse response = service.acceptSnapshot(projectId, snapshotId, StubActorConfiguration.ACTOR);
        AuditEventCreateRequest event = audit.singleEvent();

        assertThat(repository.decisionStatus).isEqualTo(ProjectSnapshotStatus.ACCEPTED);
        // Accepting a snapshot is what admits a schedule downstream, so it must name a person.
        assertThat(repository.decidedBy).isEqualTo(StubActorConfiguration.ACTOR.userId());
        assertThat(event.actorUserId()).isEqualTo(StubActorConfiguration.ACTOR.userId());
        assertThat(response.snapshot().status()).isEqualTo(ProjectSnapshotStatus.ACCEPTED);
        assertThat(response.message()).contains("No Microsoft Project file was written back.");
        assertThat(event.eventType()).isEqualTo(AuditEventTypes.IMPORT_SNAPSHOT_ACCEPTED);
        assertThat(event.projectId()).isEqualTo(projectId);
        assertThat(event.projectSnapshotId()).isEqualTo(snapshotId);
        assertThat(event.oldValueSummary()).containsEntry("status", "parsed");
        assertThat(event.newValueSummary()).containsEntry("status", "accepted");
        assertThat(event.metadata()).containsEntry("projectWriteBack", false);
    }

    @Test
    void rejectsOnlyParsedSnapshotsUsingExistingStatusValues() {
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        FakeImportReviewRepository repository = new FakeImportReviewRepository(projectId, snapshotId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ImportReviewService service = new ImportReviewService(repository, audit);

        ImportReviewDecisionResponse response = service.rejectSnapshot(projectId, snapshotId, StubActorConfiguration.ACTOR);
        AuditEventCreateRequest event = audit.singleEvent();

        assertThat(repository.decisionStatus).isEqualTo(ProjectSnapshotStatus.REJECTED);
        assertThat(response.snapshot().status()).isEqualTo(ProjectSnapshotStatus.REJECTED);
        assertThat(response.message()).contains("No Microsoft Project file was written back.");
        assertThat(event.eventType()).isEqualTo(AuditEventTypes.IMPORT_SNAPSHOT_REJECTED);
        assertThat(event.projectId()).isEqualTo(projectId);
        assertThat(event.projectSnapshotId()).isEqualTo(snapshotId);
        assertThat(event.oldValueSummary()).containsEntry("status", "parsed");
        assertThat(event.newValueSummary()).containsEntry("status", "rejected");
        assertThat(event.metadata()).containsEntry("projectWriteBack", false);
    }

    @Test
    void rejectsDecisionForAlreadyAcceptedSnapshot() {
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        FakeImportReviewRepository repository = new FakeImportReviewRepository(projectId, snapshotId);
        repository.currentStatus = ProjectSnapshotStatus.ACCEPTED;
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ImportReviewService service = new ImportReviewService(repository, audit);

        assertThatThrownBy(() -> service.acceptSnapshot(projectId, snapshotId, StubActorConfiguration.ACTOR))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Only parsed snapshots can be accepted or rejected.");
        assertThat(audit.events()).isEmpty();
    }

    @Test
    void returnsNotFoundForUnknownSnapshot() {
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        FakeImportReviewRepository repository = new FakeImportReviewRepository(projectId, snapshotId);
        repository.snapshotExists = false;
        ImportReviewService service = new ImportReviewService(repository, new CapturingAuditEventRecorder());

        assertThatThrownBy(() -> service.getSnapshot(projectId, snapshotId))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessageContaining("Project snapshot not found.");
    }

    private static class FakeImportReviewRepository implements ImportReviewRepository {

        private final UUID projectId;
        private final UUID snapshotId;
        private final UUID importBatchId = UUID.randomUUID();
        private boolean snapshotExists = true;
        private ProjectSnapshotStatus currentStatus = ProjectSnapshotStatus.PARSED;
        private ProjectSnapshotStatus decisionStatus;
        private UUID decidedBy;

        private FakeImportReviewRepository(UUID projectId, UUID snapshotId) {
            this.projectId = projectId;
            this.snapshotId = snapshotId;
        }

        @Override
        public List<ImportReviewSnapshotSummary> listSnapshots(UUID projectId) {
            return List.of(snapshot(currentStatus));
        }

        @Override
        public Optional<ImportReviewSnapshotSummary> findSnapshot(UUID projectId, UUID snapshotId) {
            if (!snapshotExists || !this.projectId.equals(projectId) || !this.snapshotId.equals(snapshotId)) {
                return Optional.empty();
            }
            return Optional.of(snapshot(currentStatus));
        }

        @Override
        public List<ImportReviewTaskRow> listTasks(UUID projectId, UUID snapshotId) {
            return List.of(
                    new ImportReviewTaskRow(
                            UUID.randomUUID(),
                            "SYN-SUMMARY-1",
                            "1",
                            "Synthetic Summary",
                            "1",
                            "1",
                            1,
                            true,
                            null,
                            null,
                            OffsetDateTime.parse("2026-01-01T08:00:00Z"),
                            OffsetDateTime.parse("2026-01-01T09:00:00Z"),
                            null,
                            null,
                            BigDecimal.ZERO,
                            null,
                            null
                    ),
                    new ImportReviewTaskRow(
                            UUID.randomUUID(),
                            "SYN-TASK-1",
                            "2",
                            "Synthetic Task A1",
                            "1.1",
                            "1.1",
                            2,
                            false,
                            "SYN-SUMMARY-1",
                            null,
                            OffsetDateTime.parse("2026-01-01T09:00:00Z"),
                            OffsetDateTime.parse("2026-01-01T10:00:00Z"),
                            null,
                            null,
                            BigDecimal.ZERO,
                            null,
                            null
                    )
            );
        }

        @Override
        public List<ImportReviewResourceRow> listResources(UUID projectId, UUID snapshotId) {
            return List.of(new ImportReviewResourceRow(
                    UUID.randomUUID(),
                    "SYN-RES-1",
                    "Synthetic Resource",
                    "work"
            ));
        }

        @Override
        public List<ImportReviewAssignmentRow> listAssignments(UUID projectId, UUID snapshotId) {
            return List.of(new ImportReviewAssignmentRow(
                    UUID.randomUUID(),
                    "SYN-ASSIGN-1",
                    "SYN-TASK-1",
                    "SYN-RES-1",
                    null,
                    null
            ));
        }

        @Override
        public List<ImportReviewExtendedAttributeRow> listExtendedAttributes(UUID projectId, UUID snapshotId) {
            return List.of(new ImportReviewExtendedAttributeRow(
                    UUID.randomUUID(),
                    ImportedExtendedAttributeEntityType.TASK,
                    "SYN-TASK-1",
                    "TEXT1",
                    "Text1",
                    "Synthetic Field",
                    "Synthetic Value"
            ));
        }

        @Override
        public Optional<ImportReviewSnapshotSummary> recordSnapshotDecision(
                UUID projectId,
                UUID snapshotId,
                ProjectSnapshotStatus status,
                UUID decidedByUserId
        ) {
            decisionStatus = status;
            decidedBy = decidedByUserId;
            currentStatus = status;
            return findSnapshot(projectId, snapshotId);
        }

        private ImportReviewSnapshotSummary snapshot(ProjectSnapshotStatus status) {
            return new ImportReviewSnapshotSummary(
                    snapshotId,
                    projectId,
                    importBatchId,
                    status,
                    "SYNTHETIC-PROJECT-1",
                    "Synthetic Basic WBS",
                    OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                    1,
                    "mpxj",
                    "16.4.0",
                    0,
                    0,
                    2,
                    1,
                    1,
                    1,
                    1,
                    1
            );
        }
    }
}
