package com.shutdowntracker.api.execution;

import java.util.List;
import java.util.UUID;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/task-progress")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class TaskProgressController {

    private final TaskProgressService service;
    private final ProjectAuthorizationService authorization;

    public TaskProgressController(TaskProgressService service, ProjectAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @PostMapping
    public TaskProgressUpdateRecord submit(
            @PathVariable UUID projectId,
            Actor actor,
            @RequestBody TaskProgressSubmitRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.SUBMIT_TASK_PROGRESS);
        return service.submit(projectId, actor, request);
    }

    @GetMapping("/supervisor-queue")
    public List<TaskProgressUpdateRecord> supervisorQueue(@PathVariable UUID projectId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.REVIEW_TASK_PROGRESS);
        return service.supervisorQueue(projectId);
    }

    @PostMapping("/{progressUpdateId}/supervisor-review")
    public TaskProgressUpdateRecord supervisorReview(
            @PathVariable UUID projectId,
            @PathVariable UUID progressUpdateId,
            Actor actor,
            @RequestBody SupervisorReviewRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.REVIEW_TASK_PROGRESS);
        return service.supervisorReview(
                projectId, progressUpdateId, actor, request.decision(), request.note());
    }

    @GetMapping("/planner-queue")
    public List<TaskProgressUpdateRecord> plannerQueue(@PathVariable UUID projectId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.PLANNER_REVIEW_TASK_PROGRESS);
        return service.plannerQueue(projectId);
    }

    /**
     * Approved updates still waiting to travel to Microsoft Project.
     *
     * <p>Gated on {@code CREATE_EXPORT_PREVIEW} rather than {@code VIEW_PROJECT}: this list is the
     * input to a preview, and is only useful to somebody about to build one. Deliberately not
     * {@code PLANNER_REVIEW_TASK_PROGRESS} either — that capability <em>produces</em> the list, and
     * keeping the producer and the consumer distinct keeps the review and approval steps visibly
     * separate one level further down.
     */
    @GetMapping("/export-queue")
    public List<TaskProgressUpdateRecord> exportQueue(@PathVariable UUID projectId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.CREATE_EXPORT_PREVIEW);
        return service.exportQueue(projectId);
    }

    @PostMapping("/{progressUpdateId}/planner-review")
    public TaskProgressUpdateRecord plannerReview(
            @PathVariable UUID projectId,
            @PathVariable UUID progressUpdateId,
            Actor actor,
            @RequestBody PlannerReviewRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.PLANNER_REVIEW_TASK_PROGRESS);
        return service.plannerReview(projectId, progressUpdateId, actor, request.approved(), request.note());
    }

    public record SupervisorReviewRequest(ProgressReviewState decision, String note) {
    }

    public record PlannerReviewRequest(boolean approved, String note) {
    }
}
