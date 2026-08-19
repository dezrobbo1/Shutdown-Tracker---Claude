package com.shutdowntracker.api.operations;

import java.util.List;
import java.util.UUID;
import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/evidence")
    public List<EvidenceRecord> evidenceForProject(@PathVariable UUID projectId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.evidenceForProject(projectId);
    }

    @GetMapping("/tasks/{importedTaskId}/evidence")
    public List<EvidenceRecord> evidenceForTask(
            @PathVariable UUID projectId, @PathVariable UUID importedTaskId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.evidenceForTask(projectId, importedTaskId);
    }

    /**
     * Uploads the file a registered evidence record is evidence of.
     *
     * <p>Separate from registration because the two can be separated in time, and because the
     * binary is the part that needs a multipart request. Capturing evidence and uploading its file
     * are the same responsibility, so they share a capability.
     */
    @PostMapping(value = "/evidence/{evidenceId}/content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvidenceRecord uploadEvidenceContent(
            @PathVariable UUID projectId,
            @PathVariable UUID evidenceId,
            Actor actor,
            @RequestParam("file") MultipartFile file
    ) {
        authorization.requireCapability(projectId, actor, Capability.CAPTURE_EVIDENCE);
        return service.uploadEvidenceContent(projectId, actor, evidenceId, file);
    }

    /**
     * Streams a stored evidence binary back.
     *
     * <p>Always an attachment with a nosniff header: evidence is whatever a field user photographed
     * or attached, and rendering it inline in the console would let an uploaded document run as
     * part of the application.
     */
    @GetMapping("/evidence/{evidenceId}/content")
    public ResponseEntity<Resource> evidenceContent(
            @PathVariable UUID projectId, @PathVariable UUID evidenceId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        EvidenceContent content = service.readEvidenceContent(projectId, evidenceId);
        EvidenceRecord record = content.record();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, record.contentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                        : record.contentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(record.originalFilename()))
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(content.content()));
    }

    /**
     * A filename reaches here from an upload, so it is quoted and stripped of anything that could
     * close the quoting or start a new header.
     */
    private String contentDisposition(String originalFilename) {
        String sanitized = originalFilename.replaceAll("[^A-Za-z0-9._ -]", "_");
        return "attachment; filename=\"" + sanitized + "\"";
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
