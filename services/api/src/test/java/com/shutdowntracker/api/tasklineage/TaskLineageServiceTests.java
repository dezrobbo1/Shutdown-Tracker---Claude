package com.shutdowntracker.api.tasklineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventTypes;
import com.shutdowntracker.api.audit.CapturingAuditEventRecorder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TaskLineageServiceTests {

    @Test
    void createsSuggestedLineageLinkWithoutMatchingEngine() {
        UUID projectId = UUID.randomUUID();
        FakeTaskLineageRepository repository = new FakeTaskLineageRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        TaskLineageService service = new TaskLineageService(repository, audit);
        TaskLineageCreateRequest request = createRequest();

        TaskLineageRecord record = service.createSuggested(projectId, request);
        AuditEventCreateRequest event = audit.singleEvent();

        assertThat(repository.createProjectId).isEqualTo(projectId);
        assertThat(repository.createRequest).isEqualTo(request);
        assertThat(record.reviewState()).isEqualTo(TaskLineageReviewState.SUGGESTED);
        assertThat(record.matchMethod()).isEqualTo("external_uid");
        assertThat(record.matchConfidence()).isEqualByComparingTo("95");
        assertThat(event.eventType()).isEqualTo(AuditEventTypes.REIMPORT_LINEAGE_LINK_CREATED);
        assertThat(event.projectId()).isEqualTo(projectId);
        assertThat(event.targetEntityId()).isEqualTo(record.id());
        assertThat(event.projectSnapshotId()).isEqualTo(record.currentSnapshotId());
        assertThat(event.oldValueSummary()).containsEntry("reviewState", "none");
        assertThat(event.newValueSummary()).containsEntry("reviewState", "suggested");
        assertThat(event.metadata())
                .containsEntry("scheduleCalculationRun", false)
                .containsEntry("projectWriteBack", false);
    }

    @Test
    void listsLineageLinksForSnapshotPair() {
        UUID projectId = UUID.randomUUID();
        FakeTaskLineageRepository repository = new FakeTaskLineageRepository(projectId);
        TaskLineageService service = new TaskLineageService(repository, new CapturingAuditEventRecorder());

        List<TaskLineageRecord> records = service.listBySnapshotPair(
                projectId,
                repository.previousSnapshotId,
                repository.currentSnapshotId
        );

        assertThat(records).hasSize(1);
        assertThat(repository.listProjectId).isEqualTo(projectId);
        assertThat(repository.listPreviousSnapshotId).isEqualTo(repository.previousSnapshotId);
        assertThat(repository.listCurrentSnapshotId).isEqualTo(repository.currentSnapshotId);
    }

    @Test
    void acceptsSuggestedLineageLinkOnly() {
        UUID projectId = UUID.randomUUID();
        FakeTaskLineageRepository repository = new FakeTaskLineageRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        TaskLineageService service = new TaskLineageService(repository, audit);

        TaskLineageDecisionResponse response = service.accept(projectId, repository.lineageLinkId);
        AuditEventCreateRequest event = audit.singleEvent();

        assertThat(repository.updatedState).isEqualTo(TaskLineageReviewState.ACCEPTED);
        assertThat(response.lineageLink().reviewState()).isEqualTo(TaskLineageReviewState.ACCEPTED);
        assertThat(response.message()).contains("No schedule calculation or Microsoft Project write-back was run.");
        assertThat(event.eventType()).isEqualTo(AuditEventTypes.REIMPORT_LINEAGE_LINK_ACCEPTED);
        assertThat(event.projectId()).isEqualTo(projectId);
        assertThat(event.targetEntityId()).isEqualTo(repository.lineageLinkId);
        assertThat(event.oldValueSummary()).containsEntry("reviewState", "suggested");
        assertThat(event.newValueSummary()).containsEntry("reviewState", "accepted");
        assertThat(event.metadata())
                .containsEntry("scheduleCalculationRun", false)
                .containsEntry("projectWriteBack", false);
    }

    @Test
    void rejectsSuggestedLineageLinkOnly() {
        UUID projectId = UUID.randomUUID();
        FakeTaskLineageRepository repository = new FakeTaskLineageRepository(projectId);
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        TaskLineageService service = new TaskLineageService(repository, audit);

        TaskLineageDecisionResponse response = service.reject(projectId, repository.lineageLinkId);
        AuditEventCreateRequest event = audit.singleEvent();

        assertThat(repository.updatedState).isEqualTo(TaskLineageReviewState.REJECTED);
        assertThat(response.lineageLink().reviewState()).isEqualTo(TaskLineageReviewState.REJECTED);
        assertThat(response.message()).contains("No schedule calculation or Microsoft Project write-back was run.");
        assertThat(event.eventType()).isEqualTo(AuditEventTypes.REIMPORT_LINEAGE_LINK_REJECTED);
        assertThat(event.projectId()).isEqualTo(projectId);
        assertThat(event.targetEntityId()).isEqualTo(repository.lineageLinkId);
        assertThat(event.oldValueSummary()).containsEntry("reviewState", "suggested");
        assertThat(event.newValueSummary()).containsEntry("reviewState", "rejected");
        assertThat(event.metadata())
                .containsEntry("scheduleCalculationRun", false)
                .containsEntry("projectWriteBack", false);
    }

    @Test
    void rejectsDecisionForAlreadyReviewedLineageLink() {
        UUID projectId = UUID.randomUUID();
        FakeTaskLineageRepository repository = new FakeTaskLineageRepository(projectId);
        repository.currentState = TaskLineageReviewState.ACCEPTED;
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        TaskLineageService service = new TaskLineageService(repository, audit);

        assertThatThrownBy(() -> service.reject(projectId, repository.lineageLinkId))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Only suggested task lineage links can be accepted or rejected.");
        assertThat(audit.events()).isEmpty();
    }

    @Test
    void returnsNotFoundForUnknownLineageLink() {
        UUID projectId = UUID.randomUUID();
        FakeTaskLineageRepository repository = new FakeTaskLineageRepository(projectId);
        repository.linkExists = false;
        TaskLineageService service = new TaskLineageService(repository, new CapturingAuditEventRecorder());

        assertThatThrownBy(() -> service.accept(projectId, repository.lineageLinkId))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessageContaining("Task lineage link not found.");
    }

    private TaskLineageCreateRequest createRequest() {
        return new TaskLineageCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "external_uid",
                BigDecimal.valueOf(95),
                null
        );
    }

    private static class FakeTaskLineageRepository implements TaskLineageRepository {

        private final UUID projectId;
        private final UUID lineageLinkId = UUID.randomUUID();
        private final UUID previousSnapshotId = UUID.randomUUID();
        private final UUID currentSnapshotId = UUID.randomUUID();
        private final UUID previousImportedTaskId = UUID.randomUUID();
        private final UUID currentImportedTaskId = UUID.randomUUID();
        private boolean linkExists = true;
        private TaskLineageReviewState currentState = TaskLineageReviewState.SUGGESTED;
        private UUID createProjectId;
        private TaskLineageCreateRequest createRequest;
        private UUID listProjectId;
        private UUID listPreviousSnapshotId;
        private UUID listCurrentSnapshotId;
        private TaskLineageReviewState updatedState;

        private FakeTaskLineageRepository(UUID projectId) {
            this.projectId = projectId;
        }

        @Override
        public TaskLineageRecord create(UUID projectId, TaskLineageCreateRequest request) {
            createProjectId = projectId;
            createRequest = request;
            return record(TaskLineageReviewState.SUGGESTED);
        }

        @Override
        public List<TaskLineageRecord> listBySnapshotPair(
                UUID projectId,
                UUID previousSnapshotId,
                UUID currentSnapshotId
        ) {
            listProjectId = projectId;
            listPreviousSnapshotId = previousSnapshotId;
            listCurrentSnapshotId = currentSnapshotId;
            return List.of(record(currentState));
        }

        @Override
        public Optional<TaskLineageRecord> find(UUID projectId, UUID lineageLinkId) {
            if (!linkExists || !this.projectId.equals(projectId) || !this.lineageLinkId.equals(lineageLinkId)) {
                return Optional.empty();
            }
            return Optional.of(record(currentState));
        }

        @Override
        public Optional<TaskLineageRecord> updateReviewState(
                UUID projectId,
                UUID lineageLinkId,
                TaskLineageReviewState reviewState
        ) {
            updatedState = reviewState;
            currentState = reviewState;
            return find(projectId, lineageLinkId);
        }

        private TaskLineageRecord record(TaskLineageReviewState reviewState) {
            return new TaskLineageRecord(
                    lineageLinkId,
                    projectId,
                    previousSnapshotId,
                    currentSnapshotId,
                    previousImportedTaskId,
                    "SYN-TASK-1",
                    "Synthetic Task A1",
                    currentImportedTaskId,
                    "SYN-TASK-1",
                    "Synthetic Task A1 Revised",
                    "external_uid",
                    BigDecimal.valueOf(95),
                    reviewState,
                    null,
                    null
            );
        }
    }
}
