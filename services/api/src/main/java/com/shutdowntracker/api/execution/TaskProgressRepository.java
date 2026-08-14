package com.shutdowntracker.api.execution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskProgressRepository {

    TaskProgressUpdateRecord submit(UUID projectId, UUID submittedByUserId, TaskProgressSubmitRequest request);

    Optional<TaskProgressUpdateRecord> find(UUID projectId, UUID progressUpdateId);

    /** Finds an existing submission for a device-supplied key, so a retry returns the original. */
    Optional<TaskProgressUpdateRecord> findByIdempotencyKey(UUID projectId, String idempotencyKey);

    TaskProgressUpdateRecord recordSupervisorDecision(
            UUID progressUpdateId,
            ProgressReviewState decision,
            PlannerReviewState plannerReviewState,
            UUID reviewedByUserId,
            String note);

    TaskProgressUpdateRecord recordPlannerDecision(
            UUID progressUpdateId,
            PlannerReviewState decision,
            ProgressExportState exportState,
            UUID reviewedByUserId,
            String note);

    List<TaskProgressUpdateRecord> findSupervisorQueue(UUID projectId);

    List<TaskProgressUpdateRecord> findPlannerQueue(UUID projectId);

    /** Marks a superseded update so a correction does not leave two live values on one task. */
    void markSuperseded(UUID progressUpdateId);

    /** True when the imported task is a summary task, whose actuals must never be exported. */
    boolean isSummaryTask(UUID projectId, UUID importedTaskId);

    void upsertExecutionState(
            UUID projectId,
            UUID importedTaskId,
            TaskExecutionState state,
            UUID changedByUserId,
            String reason);

    Optional<TaskExecutionState> findExecutionState(UUID importedTaskId);
}
