package com.shutdowntracker.api.tasklineage;

import static com.shutdowntracker.api.tasklineage.TaskLineageRecordValidation.requireNonNull;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.audit.AuditEventCategory;
import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventRecorder;
import com.shutdowntracker.api.audit.AuditEventTypes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class TaskLineageService {

    private static final String ACCEPTED_MESSAGE = "Task lineage link accepted for import review only. "
            + "No schedule calculation or Microsoft Project write-back was run.";
    private static final String REJECTED_MESSAGE = "Task lineage link rejected for import review only. "
            + "No schedule calculation or Microsoft Project write-back was run.";

    private final TaskLineageRepository repository;
    private final AuditEventRecorder auditEventRecorder;

    public TaskLineageService(TaskLineageRepository repository, AuditEventRecorder auditEventRecorder) {
        this.repository = repository;
        this.auditEventRecorder = auditEventRecorder;
    }

    @Transactional
    public TaskLineageRecord createSuggested(UUID projectId, TaskLineageCreateRequest request, Actor actor) {
        requireNonNull(actor, "actor is required.");
        UUID requiredProjectId = requireNonNull(projectId, "projectId is required.");
        TaskLineageCreateRequest requiredRequest = requireNonNull(request, "request is required.");
        TaskLineageRecord record = repository.create(requiredProjectId, requiredRequest);

        // A person proposes a lineage match, so the audit row names them rather than the system.
        auditEventRecorder.record(AuditEventCreateRequest.userEvent(
                requiredProjectId,
                actor.userId(),
                actor.displayName(),
                actor.role(),
                AuditEventCategory.REIMPORT,
                AuditEventTypes.REIMPORT_LINEAGE_LINK_CREATED,
                "task_lineage_link",
                record.id(),
                targetDisplayName(record),
                Map.of("reviewState", "none"),
                Map.of("reviewState", record.reviewState().databaseValue()),
                "Task lineage link suggested for import review only. No schedule calculation or Project write-back was run.",
                record.currentSnapshotId(),
                null,
                lineageMetadata(record)
        ));

        return record;
    }

    @Transactional(readOnly = true)
    public List<TaskLineageRecord> listBySnapshotPair(
            UUID projectId,
            UUID previousSnapshotId,
            UUID currentSnapshotId
    ) {
        return repository.listBySnapshotPair(
                requireNonNull(projectId, "projectId is required."),
                requireNonNull(previousSnapshotId, "previousSnapshotId is required."),
                requireNonNull(currentSnapshotId, "currentSnapshotId is required.")
        );
    }

    @Transactional
    public TaskLineageDecisionResponse accept(UUID projectId, UUID lineageLinkId, Actor actor) {
        return recordReviewDecision(
                projectId, lineageLinkId, actor, TaskLineageReviewState.ACCEPTED, ACCEPTED_MESSAGE);
    }

    @Transactional
    public TaskLineageDecisionResponse reject(UUID projectId, UUID lineageLinkId, Actor actor) {
        return recordReviewDecision(
                projectId, lineageLinkId, actor, TaskLineageReviewState.REJECTED, REJECTED_MESSAGE);
    }

    private TaskLineageDecisionResponse recordReviewDecision(
            UUID projectId,
            UUID lineageLinkId,
            Actor actor,
            TaskLineageReviewState targetState,
            String message
    ) {
        requireNonNull(actor, "actor is required.");
        UUID requiredProjectId = requireNonNull(projectId, "projectId is required.");
        UUID requiredLineageLinkId = requireNonNull(lineageLinkId, "lineageLinkId is required.");
        TaskLineageRecord existing = find(requiredProjectId, requiredLineageLinkId);

        if (existing.reviewState() != TaskLineageReviewState.SUGGESTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only suggested task lineage links can be accepted or rejected."
            );
        }

        TaskLineageRecord updated = repository
                .updateReviewState(requiredProjectId, requiredLineageLinkId, targetState, actor.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Task lineage review decision could not be recorded because the link is no longer suggested."
                ));

        // Reconciling lineage across a re-import is a planner's judgement, so name them.
        auditEventRecorder.record(AuditEventCreateRequest.userEvent(
                requiredProjectId,
                actor.userId(),
                actor.displayName(),
                actor.role(),
                AuditEventCategory.REIMPORT,
                auditEventType(targetState),
                "task_lineage_link",
                updated.id(),
                targetDisplayName(updated),
                Map.of("reviewState", existing.reviewState().databaseValue()),
                Map.of("reviewState", updated.reviewState().databaseValue()),
                message,
                updated.currentSnapshotId(),
                null,
                lineageMetadata(updated)
        ));

        return new TaskLineageDecisionResponse(updated, message);
    }

    private String auditEventType(TaskLineageReviewState reviewState) {
        return switch (reviewState) {
            case ACCEPTED -> AuditEventTypes.REIMPORT_LINEAGE_LINK_ACCEPTED;
            case REJECTED -> AuditEventTypes.REIMPORT_LINEAGE_LINK_REJECTED;
            default -> throw new IllegalArgumentException("Unsupported task lineage audit state: " + reviewState);
        };
    }

    private String targetDisplayName(TaskLineageRecord record) {
        String previousName = record.previousTaskName() == null ? "previous task" : record.previousTaskName();
        String currentName = record.currentTaskName() == null ? "current task" : record.currentTaskName();
        return previousName + " -> " + currentName;
    }

    private Map<String, Object> lineageMetadata(TaskLineageRecord record) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("previousSnapshotId", record.previousSnapshotId().toString());
        metadata.put("currentSnapshotId", record.currentSnapshotId().toString());
        metadata.put("previousImportedTaskId", record.previousImportedTaskId().toString());
        metadata.put("currentImportedTaskId", record.currentImportedTaskId().toString());
        metadata.put("matchMethod", record.matchMethod());
        if (record.matchConfidence() != null) {
            metadata.put("matchConfidence", record.matchConfidence());
        }
        metadata.put("scheduleCalculationRun", false);
        metadata.put("projectWriteBack", false);
        return metadata;
    }

    private TaskLineageRecord find(UUID projectId, UUID lineageLinkId) {
        return repository.find(projectId, lineageLinkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task lineage link not found."));
    }
}
