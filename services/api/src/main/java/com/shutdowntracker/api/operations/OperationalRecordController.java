package com.shutdowntracker.api.operations;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class OperationalRecordController {

    private final OperationalRecordService service;
    private final ProjectAuthorizationService authorization;

    public OperationalRecordController(
            OperationalRecordService service,
            ProjectAuthorizationService authorization
    ) {
        this.service = service;
        this.authorization = authorization;
    }

    @PostMapping("/problems")
    public ProblemRecord raiseProblem(
            @PathVariable UUID projectId, Actor actor, @RequestBody ProblemCreateRequest request) {
        authorization.requireCapability(projectId, actor, Capability.RAISE_PROBLEM);
        return service.raiseProblem(projectId, actor, request);
    }

    @GetMapping("/problems")
    public List<ProblemRecord> openProblems(@PathVariable UUID projectId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.openProblems(projectId);
    }

    @PostMapping("/problems/{problemId}/assign")
    public ProblemRecord assignProblem(
            @PathVariable UUID projectId,
            @PathVariable UUID problemId,
            Actor actor,
            @RequestParam UUID assigneeUserId
    ) {
        authorization.requireCapability(projectId, actor, Capability.MANAGE_PROBLEM);
        return service.assignProblem(projectId, actor, problemId, assigneeUserId);
    }

    @PostMapping("/problems/{problemId}/close")
    public ProblemRecord closeProblem(
            @PathVariable UUID projectId,
            @PathVariable UUID problemId,
            Actor actor,
            @RequestBody(required = false) CloseProblemRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.MANAGE_PROBLEM);
        return service.closeProblem(projectId, actor, problemId, request == null ? null : request.resolutionNote());
    }

    @PostMapping("/actions")
    public ActionRecord createAction(
            @PathVariable UUID projectId, Actor actor, @RequestBody ActionCreateRequest request) {
        authorization.requireCapability(projectId, actor, Capability.MANAGE_ACTION);
        return service.createAction(projectId, actor, request);
    }

    @GetMapping("/actions")
    public List<ActionRecord> openActions(@PathVariable UUID projectId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.openActions(projectId);
    }

    @PostMapping("/actions/{actionId}/complete")
    public ActionRecord completeAction(
            @PathVariable UUID projectId, @PathVariable UUID actionId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.MANAGE_ACTION);
        return service.completeAction(projectId, actor, actionId);
    }

    @PostMapping("/evidence")
    public EvidenceRecord registerEvidence(
            @PathVariable UUID projectId, Actor actor, @RequestBody EvidenceCreateRequest request) {
        authorization.requireCapability(projectId, actor, Capability.CAPTURE_EVIDENCE);
        return service.registerEvidence(projectId, actor, request);
    }

    @GetMapping("/tasks/{importedTaskId}/evidence")
    public List<EvidenceRecord> evidenceForTask(
            @PathVariable UUID projectId, @PathVariable UUID importedTaskId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.evidenceForTask(projectId, importedTaskId);
    }

    @PostMapping("/handover-notes")
    public HandoverNoteRecord createHandoverNote(
            @PathVariable UUID projectId, Actor actor, @RequestBody HandoverNoteCreateRequest request) {
        authorization.requireCapability(projectId, actor, Capability.RECORD_HANDOVER);
        return service.createHandoverNote(projectId, actor, request);
    }

    @GetMapping("/handover-notes/unacknowledged")
    public List<HandoverNoteRecord> unacknowledgedHandoverNotes(@PathVariable UUID projectId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.unacknowledgedHandoverNotes(projectId);
    }

    @PostMapping("/handover-notes/{handoverNoteId}/acknowledge")
    public HandoverNoteRecord acknowledgeHandoverNote(
            @PathVariable UUID projectId, @PathVariable UUID handoverNoteId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.RECORD_HANDOVER);
        return service.acknowledgeHandoverNote(projectId, actor, handoverNoteId);
    }

    public record CloseProblemRequest(String resolutionNote) {
    }
}
