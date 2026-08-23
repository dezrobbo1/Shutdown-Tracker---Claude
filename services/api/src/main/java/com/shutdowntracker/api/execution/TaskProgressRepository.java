package com.shutdowntracker.api.execution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskProgressRepository extends ExportBatchProgressBinding {

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

    /**
     * Approved updates that may still travel to Microsoft Project.
     *
     * <p>The third queue in one chain: the field submits, a supervisor validates, a planner
     * approves, and what survives all three is what an export preview may be built from.
     *
     * <p>Keyed on {@code export_state}, never on the planner's decision. A correction supersedes an
     * earlier update by setting {@code export_state} alone — {@code planner_review_state} keeps
     * saying {@code planner_approved}, because the planner did approve it, once. Reading the
     * decision to infer the consequence would therefore offer a value that has since been replaced.
     */
    List<TaskProgressUpdateRecord> findExportQueue(UUID projectId);

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
