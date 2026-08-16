package com.shutdowntracker.api.execution;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.audit.AuditEventCategory;
import com.shutdowntracker.api.audit.AuditEventCreateRequest;
import com.shutdowntracker.api.audit.AuditEventRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The bridge from field execution to export eligibility.
 *
 * <p>Implements the chain in {@code docs/product/task-progress-review-export-approval.md}:
 * a field submission is checked by a supervisor for operational validity, then by a
 * planner for whether it is safe to send toward Microsoft Project. The two decisions are
 * deliberately separate — supervisor acceptance never confers export eligibility.
 *
 * <p>Two product rules are enforced here rather than left to the caller:
 * only imported leaf tasks can produce export candidates, and only percent complete,
 * actual start, and actual finish are candidate fields. Everything else is operational
 * context that stays inside Shutdown Tracker.
 */
@Service
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class TaskProgressService {

    private final TaskProgressRepository repository;
    private final AuditEventRecorder auditEventRecorder;

    public TaskProgressService(TaskProgressRepository repository, AuditEventRecorder auditEventRecorder) {
        this.repository = repository;
        this.auditEventRecorder = auditEventRecorder;
    }

    /**
     * Records a field submission and moves the task's execution state to match.
     *
     * <p>A repeated {@code idempotencyKey} returns the original submission rather than
     * creating a second one, so a field device retrying over a poor connection cannot
     * double-report progress.
     */
    @Transactional
    public TaskProgressUpdateRecord submit(UUID projectId, Actor actor, TaskProgressSubmitRequest request) {
        Objects.requireNonNull(projectId, "projectId is required.");
        Objects.requireNonNull(actor, "actor is required.");
        Objects.requireNonNull(request, "request is required.");

        if (request.idempotencyKey() != null) {
            var existing = repository.findByIdempotencyKey(projectId, request.idempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        // A correction supersedes the earlier value instead of overwriting it, so the
        // original field claim stays visible in the record.
        if (request.supersedesProgressUpdateId() != null) {
            TaskProgressUpdateRecord superseded = repository
                    .find(projectId, request.supersedesProgressUpdateId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Superseded progress update not found."));
            repository.markSuperseded(superseded.id());
        }

        TaskProgressUpdateRecord submitted = repository.submit(projectId, actor.userId(), request);
        repository.upsertExecutionState(
                projectId, request.importedTaskId(), request.executionState(), actor.userId(), request.comment());

        auditEventRecorder.record(AuditEventCreateRequest.userEvent(
                projectId, actor.userId(), actor.displayName(), actor.role(),
                AuditEventCategory.IMPORT, "task.progress.submitted",
                "task_progress_update", submitted.id(), "Task progress update",
                Map.of(), Map.of("executionState", request.executionState().databaseValue()),
                request.comment(), null, null, Map.of("projectWriteBack", false)));

        return submitted;
    }

    /**
     * Records the supervisor's operational judgement.
     *
     * <p>Accepting routes the update to the planner queue only when it could actually
     * produce an export candidate: a leaf task carrying at least one whitelisted field.
     * Anything else settles at {@code not_required} so the planner queue stays meaningful.
     */
    @Transactional
    public TaskProgressUpdateRecord supervisorReview(
            UUID projectId,
            UUID progressUpdateId,
            Actor actor,
            ProgressReviewState decision,
            String note
    ) {
        TaskProgressUpdateRecord update = require(projectId, progressUpdateId);

        if (decision != ProgressReviewState.SUPERVISOR_ACCEPTED
                && decision != ProgressReviewState.CORRECTION_REQUESTED
                && decision != ProgressReviewState.REJECTED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A supervisor may accept, request correction, or reject.");
        }
        if (!update.progressReviewState().isAwaitingSupervisor()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Only a submitted update awaits supervisor review.");
        }

        PlannerReviewState plannerReviewState = PlannerReviewState.NOT_REQUIRED;
        if (decision == ProgressReviewState.SUPERVISOR_ACCEPTED && producesExportCandidate(projectId, update)) {
            plannerReviewState = PlannerReviewState.NEEDS_PLANNER_REVIEW;
        }

        TaskProgressUpdateRecord reviewed = repository.recordSupervisorDecision(
                progressUpdateId, decision, plannerReviewState, actor.userId(), note);

        auditEventRecorder.record(AuditEventCreateRequest.userEvent(
                projectId, actor.userId(), actor.displayName(), actor.role(),
                AuditEventCategory.APPROVAL, "task.progress.supervisor_reviewed",
                "task_progress_update", progressUpdateId, "Supervisor review",
                Map.of("progressReviewState", update.progressReviewState().databaseValue()),
                Map.of("progressReviewState", decision.databaseValue()),
                note, null, null,
                // Recorded explicitly because the distinction matters: operational
                // acceptance is not approval to send anything to Microsoft Project.
                Map.of("approvesMicrosoftProjectExport", false)));

        return reviewed;
    }

    /**
     * Records the planner's decision on whether the value may travel toward Microsoft
     * Project. Approval marks the value eligible for export preview. It does not update
     * the master {@code .mpp}, and nothing in this product does.
     */
    @Transactional
    public TaskProgressUpdateRecord plannerReview(
            UUID projectId,
            UUID progressUpdateId,
            Actor actor,
            boolean approved,
            String note
    ) {
        TaskProgressUpdateRecord update = require(projectId, progressUpdateId);

        if (update.plannerReviewState() != PlannerReviewState.NEEDS_PLANNER_REVIEW) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "This update is not awaiting planner review.");
        }

        PlannerReviewState decision = approved
                ? PlannerReviewState.PLANNER_APPROVED
                : PlannerReviewState.PLANNER_REJECTED;
        ProgressExportState exportState = approved
                ? ProgressExportState.ELIGIBLE
                : ProgressExportState.NOT_ELIGIBLE;

        TaskProgressUpdateRecord reviewed = repository.recordPlannerDecision(
                progressUpdateId, decision, exportState, actor.userId(), note);

        auditEventRecorder.record(AuditEventCreateRequest.userEvent(
                projectId, actor.userId(), actor.displayName(), actor.role(),
                AuditEventCategory.APPROVAL, "task.progress.planner_reviewed",
                "task_progress_update", progressUpdateId, "Planner review",
                Map.of("exportState", update.exportState().databaseValue()),
                Map.of("exportState", exportState.databaseValue()),
                note, null, null,
                Map.of("approvesMicrosoftProjectExport", false, "projectWriteBack", false)));

        return reviewed;
    }

    public List<TaskProgressUpdateRecord> supervisorQueue(UUID projectId) {
        return repository.findSupervisorQueue(projectId);
    }

    public List<TaskProgressUpdateRecord> plannerQueue(UUID projectId) {
        return repository.findPlannerQueue(projectId);
    }

    /**
     * Whether this update could become an export candidate at all.
     *
     * <p>Summary-task actuals are never exported; Microsoft Project rolls those up itself.
     * An update carrying only a comment or a physical percent complete is operational
     * context and needs no planner decision.
     */
    private boolean producesExportCandidate(UUID projectId, TaskProgressUpdateRecord update) {
        if (repository.isSummaryTask(projectId, update.importedTaskId())) {
            return false;
        }
        return update.percentComplete() != null
                || update.actualStart() != null
                || update.actualFinish() != null;
    }

    private TaskProgressUpdateRecord require(UUID projectId, UUID progressUpdateId) {
        return repository.find(projectId, progressUpdateId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Progress update not found."));
    }
}
