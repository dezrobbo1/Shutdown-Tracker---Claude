package com.shutdowntracker.api.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.audit.AuditActorType;
import com.shutdowntracker.api.audit.AuditEventCategory;
import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventTypes;
import com.shutdowntracker.api.audit.CapturingAuditEventRecorder;
import com.shutdowntracker.api.exportpreview.ApprovalState;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ApprovalServiceTests {

    private static final Actor ACTOR =
            new Actor(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "planner", "Synthetic Planner");

    @Test
    void recordsApprovedForExportDecisionAndAuditEvent() {
        UUID projectId = UUID.randomUUID();
        UUID sourceEntityId = UUID.randomUUID();
        FakeApprovalRepository repository = new FakeApprovalRepository();
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ApprovalService service = new ApprovalService(repository, audit);

        ApprovalRecord record = service.recordDecision(projectId, ACTOR, new ApprovalRecordCreateRequest(
                "task_update",
                sourceEntityId,
                ApprovalState.APPROVED_FOR_EXPORT,
                "Synthetic planner approval",
                null
        ));

        assertThat(record.approvalState()).isEqualTo(ApprovalState.APPROVED_FOR_EXPORT);
        assertThat(record.reviewedByUserId()).isEqualTo(ACTOR.userId());

        AuditEventCreateRequest event = audit.events().getLast();
        assertThat(event.eventCategory()).isEqualTo(AuditEventCategory.APPROVAL);
        assertThat(event.eventType()).isEqualTo(AuditEventTypes.APPROVAL_APPROVED_FOR_EXPORT);
        assertThat(event.actorType()).isEqualTo(AuditActorType.USER);
        assertThat(event.actorUserId()).isEqualTo(ACTOR.userId());
        assertThat(event.newValueSummary()).containsEntry("approvalState", "approved_for_export");
        assertThat(event.metadata()).containsEntry("exportBatchApproval", false);
        assertThat(event.metadata()).containsEntry("projectWriteBack", false);
    }

    @Test
    void supersedesThePreviousDecisionRatherThanEditingIt() {
        UUID projectId = UUID.randomUUID();
        UUID sourceEntityId = UUID.randomUUID();
        FakeApprovalRepository repository = new FakeApprovalRepository();
        ApprovalService service = new ApprovalService(repository, new CapturingAuditEventRecorder());

        service.recordDecision(projectId, ACTOR, request(sourceEntityId, ApprovalState.APPROVED_FOR_EXPORT));
        service.recordDecision(projectId, ACTOR, request(sourceEntityId, ApprovalState.REJECTED));

        List<ApprovalRecord> history = repository.listBySourceEntity(projectId, "task_update", sourceEntityId);
        assertThat(history).hasSize(2);
        assertThat(history.getFirst().approvalState()).isEqualTo(ApprovalState.SUPERSEDED);
        assertThat(history.getLast().approvalState()).isEqualTo(ApprovalState.REJECTED);
    }

    @Test
    void recordsThePreviousStateOnTheAuditEvent() {
        UUID projectId = UUID.randomUUID();
        UUID sourceEntityId = UUID.randomUUID();
        FakeApprovalRepository repository = new FakeApprovalRepository();
        CapturingAuditEventRecorder audit = new CapturingAuditEventRecorder();
        ApprovalService service = new ApprovalService(repository, audit);

        service.recordDecision(projectId, ACTOR, request(sourceEntityId, ApprovalState.SUBMITTED));
        service.recordDecision(projectId, ACTOR, request(sourceEntityId, ApprovalState.APPROVED_FOR_EXPORT));

        assertThat(audit.events().getFirst().oldValueSummary()).containsEntry("approvalState", "none");
        assertThat(audit.events().getLast().oldValueSummary()).containsEntry("approvalState", "submitted");
    }

    @Test
    void rejectsSystemOwnedStatesFromBeingRecordedDirectly() {
        ApprovalService service =
                new ApprovalService(new FakeApprovalRepository(), new CapturingAuditEventRecorder());

        assertThatThrownBy(() -> service.recordDecision(
                UUID.randomUUID(),
                ACTOR,
                request(UUID.randomUUID(), ApprovalState.SUPERSEDED)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot be recorded directly");

        assertThatThrownBy(() -> service.recordDecision(
                UUID.randomUUID(),
                ACTOR,
                request(UUID.randomUUID(), ApprovalState.EXPORTED)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot be recorded directly");
    }

    @Test
    void requiresAnActor() {
        ApprovalService service =
                new ApprovalService(new FakeApprovalRepository(), new CapturingAuditEventRecorder());

        assertThatThrownBy(() -> service.recordDecision(
                UUID.randomUUID(),
                null,
                request(UUID.randomUUID(), ApprovalState.APPROVED_FOR_EXPORT)
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("actor is required.");
    }

    private ApprovalRecordCreateRequest request(UUID sourceEntityId, ApprovalState state) {
        return new ApprovalRecordCreateRequest("task_update", sourceEntityId, state, "Synthetic decision", null);
    }

    private static class FakeApprovalRepository implements ApprovalRepository {

        private final List<ApprovalRecord> records = new ArrayList<>();

        @Override
        public int supersedeActiveApprovals(UUID projectId, String sourceEntityType, UUID sourceEntityId) {
            int superseded = 0;
            for (int index = 0; index < records.size(); index++) {
                ApprovalRecord record = records.get(index);
                boolean terminal = record.approvalState() == ApprovalState.SUPERSEDED
                        || record.approvalState() == ApprovalState.REJECTED
                        || record.approvalState() == ApprovalState.EXPORTED;
                if (matches(record, projectId, sourceEntityType, sourceEntityId) && !terminal) {
                    records.set(index, supersede(record));
                    superseded++;
                }
            }
            return superseded;
        }

        @Override
        public ApprovalRecord create(
                UUID projectId,
                UUID reviewedByUserId,
                ApprovalRecordCreateRequest request,
                Map<String, Object> metadata
        ) {
            ApprovalRecord record = new ApprovalRecord(
                    UUID.randomUUID(),
                    projectId,
                    request.sourceEntityType(),
                    request.sourceEntityId(),
                    request.approvalState(),
                    reviewedByUserId,
                    OffsetDateTime.now(),
                    reviewedByUserId,
                    OffsetDateTime.now(),
                    request.reason()
            );
            records.add(record);
            return record;
        }

        @Override
        public Optional<ApprovalRecord> findLatest(UUID projectId, String sourceEntityType, UUID sourceEntityId) {
            return listBySourceEntity(projectId, sourceEntityType, sourceEntityId).stream()
                    .reduce((first, second) -> second);
        }

        @Override
        public List<ApprovalRecord> listBySourceEntity(UUID projectId, String sourceEntityType, UUID sourceEntityId) {
            return records.stream()
                    .filter(record -> matches(record, projectId, sourceEntityType, sourceEntityId))
                    .toList();
        }

        private boolean matches(ApprovalRecord record, UUID projectId, String type, UUID id) {
            return record.projectId().equals(projectId)
                    && record.sourceEntityType().equals(type)
                    && record.sourceEntityId().equals(id);
        }

        private ApprovalRecord supersede(ApprovalRecord record) {
            return new ApprovalRecord(
                    record.id(),
                    record.projectId(),
                    record.sourceEntityType(),
                    record.sourceEntityId(),
                    ApprovalState.SUPERSEDED,
                    record.requestedByUserId(),
                    record.requestedAt(),
                    record.reviewedByUserId(),
                    record.reviewedAt(),
                    record.reason()
            );
        }
    }
}
