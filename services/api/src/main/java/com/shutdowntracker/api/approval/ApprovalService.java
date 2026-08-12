package com.shutdowntracker.api.approval;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.audit.AuditEventCategory;
import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventRecorder;
import com.shutdowntracker.api.audit.AuditEventTypes;
import com.shutdowntracker.api.exportpreview.ApprovalState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Records review decisions on operational records that may become export candidates.
 *
 * <p>This is the gate the export preview reads. Without an approval record in
 * {@code approved_for_export}, no preview line is export-eligible and no export batch can be approved.
 *
 * <p>Approving a source record is not export batch approval. Export batch approval remains a separate,
 * later decision on the assembled batch.
 */
@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ApprovalService {

    private static final String RECORDED_MESSAGE = "Approval decision recorded. "
            + "No MSPDI/XML artifact was generated and no Microsoft Project write-back was run.";

    /** States a caller may record directly. Terminal bookkeeping states are set by the system, not by a request. */
    private static final List<ApprovalState> RECORDABLE_STATES = List.of(
            ApprovalState.DRAFT,
            ApprovalState.SUBMITTED,
            ApprovalState.AWAITING_REVIEW,
            ApprovalState.CORRECTION_REQUESTED,
            ApprovalState.APPROVED_FOR_EXPORT,
            ApprovalState.REJECTED
    );

    private final ApprovalRepository repository;
    private final AuditEventRecorder auditEventRecorder;

    public ApprovalService(ApprovalRepository repository, AuditEventRecorder auditEventRecorder) {
        this.repository = repository;
        this.auditEventRecorder = auditEventRecorder;
    }

    @Transactional
    public ApprovalRecord recordDecision(UUID projectId, Actor actor, ApprovalRecordCreateRequest request) {
        UUID requiredProjectId = Objects.requireNonNull(projectId, "projectId is required.");
        Actor requiredActor = Objects.requireNonNull(actor, "actor is required.");
        ApprovalRecordCreateRequest requiredRequest = Objects.requireNonNull(request, "request is required.");

        if (!RECORDABLE_STATES.contains(requiredRequest.approvalState())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Approval state " + requiredRequest.approvalState().databaseValue()
                            + " cannot be recorded directly."
            );
        }

        ApprovalState previousState = repository
                .findLatest(requiredProjectId, requiredRequest.sourceEntityType(), requiredRequest.sourceEntityId())
                .map(ApprovalRecord::approvalState)
                .orElse(null);

        // A new decision supersedes the previous one rather than editing it, preserving approval history.
        int supersededCount = repository.supersedeActiveApprovals(
                requiredProjectId,
                requiredRequest.sourceEntityType(),
                requiredRequest.sourceEntityId()
        );

        ApprovalRecord created = repository.create(
                requiredProjectId,
                requiredActor.userId(),
                requiredRequest,
                decisionMetadata(requiredRequest, supersededCount)
        );

        auditEventRecorder.record(AuditEventCreateRequest.userEvent(
                requiredProjectId,
                requiredActor.userId(),
                requiredActor.displayName(),
                requiredActor.role(),
                AuditEventCategory.APPROVAL,
                auditEventType(created.approvalState()),
                requiredRequest.sourceEntityType(),
                created.id(),
                requiredRequest.sourceEntityType() + " approval",
                previousState == null ? Map.of("approvalState", "none")
                        : Map.of("approvalState", previousState.databaseValue()),
                Map.of("approvalState", created.approvalState().databaseValue()),
                requiredRequest.reason() == null ? RECORDED_MESSAGE : requiredRequest.reason(),
                null,
                null,
                decisionMetadata(requiredRequest, supersededCount)
        ));

        return created;
    }

    @Transactional(readOnly = true)
    public List<ApprovalRecord> listBySourceEntity(UUID projectId, String sourceEntityType, UUID sourceEntityId) {
        Objects.requireNonNull(projectId, "projectId is required.");
        return repository.listBySourceEntity(projectId, sourceEntityType, sourceEntityId);
    }

    private Map<String, Object> decisionMetadata(ApprovalRecordCreateRequest request, int supersededCount) {
        Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
        metadata.put("sourceEntityType", request.sourceEntityType());
        metadata.put("sourceEntityId", request.sourceEntityId().toString());
        metadata.put("approvalState", request.approvalState().databaseValue());
        metadata.put("supersededApprovalCount", supersededCount);
        metadata.put("exportBatchApproval", false);
        metadata.put("projectWriteBack", false);
        return metadata;
    }

    private String auditEventType(ApprovalState state) {
        return switch (state) {
            case APPROVED_FOR_EXPORT -> AuditEventTypes.APPROVAL_APPROVED_FOR_EXPORT;
            case REJECTED -> AuditEventTypes.APPROVAL_REJECTED;
            case CORRECTION_REQUESTED -> AuditEventTypes.APPROVAL_CORRECTION_REQUESTED;
            default -> AuditEventTypes.APPROVAL_RECORDED;
        };
    }
}
