package com.shutdowntracker.api.tasklineage;

import static com.shutdowntracker.api.tasklineage.TaskLineageRecordValidation.requireNonNull;

import java.util.List;
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

    public TaskLineageService(TaskLineageRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TaskLineageRecord createSuggested(UUID projectId, TaskLineageCreateRequest request) {
        return repository.create(
                requireNonNull(projectId, "projectId is required."),
                requireNonNull(request, "request is required.")
        );
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
    public TaskLineageDecisionResponse accept(UUID projectId, UUID lineageLinkId) {
        return recordReviewDecision(projectId, lineageLinkId, TaskLineageReviewState.ACCEPTED, ACCEPTED_MESSAGE);
    }

    @Transactional
    public TaskLineageDecisionResponse reject(UUID projectId, UUID lineageLinkId) {
        return recordReviewDecision(projectId, lineageLinkId, TaskLineageReviewState.REJECTED, REJECTED_MESSAGE);
    }

    private TaskLineageDecisionResponse recordReviewDecision(
            UUID projectId,
            UUID lineageLinkId,
            TaskLineageReviewState targetState,
            String message
    ) {
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
                .updateReviewState(requiredProjectId, requiredLineageLinkId, targetState)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Task lineage review decision could not be recorded because the link is no longer suggested."
                ));

        return new TaskLineageDecisionResponse(updated, message);
    }

    private TaskLineageRecord find(UUID projectId, UUID lineageLinkId) {
        return repository.find(projectId, lineageLinkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task lineage link not found."));
    }
}
