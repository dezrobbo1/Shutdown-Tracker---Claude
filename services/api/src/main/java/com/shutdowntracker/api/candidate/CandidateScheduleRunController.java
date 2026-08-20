package com.shutdowntracker.api.candidate;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/projects/{projectId}")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class CandidateScheduleRunController {

    private final CandidateScheduleRunService service;
    private final ProjectAuthorizationService authorization;

    public CandidateScheduleRunController(
            CandidateScheduleRunService service, ProjectAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    /**
     * Records the candidate schedule Microsoft Project calculated from one export batch's artifact.
     *
     * <p>Nested under the batch because that is what a candidate is bound to: the approved inputs
     * that were written into the artifact Project opened are the only reason its recalculated
     * values mean anything.
     */
    @PostMapping(
            path = "/export-preview/{exportBatchId}/candidate-runs",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CandidateScheduleRunRecord returnCandidate(
            @PathVariable UUID projectId,
            @PathVariable UUID exportBatchId,
            Actor actor,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "microsoftProjectVersion", required = false) String microsoftProjectVersion,
            @RequestParam(value = "plannerNote", required = false) String plannerNote
    ) {
        authorization.requireCapability(projectId, actor, Capability.RETURN_CANDIDATE_SCHEDULE);
        return service.returnCandidate(projectId, exportBatchId, actor, file, microsoftProjectVersion, plannerNote);
    }

    /**
     * The candidate calculations recorded against one export batch, newest first.
     *
     * <p>Readable by anyone who can view the project. That a candidate came back, when, and from
     * which source is operational information a supervisor or coordinator has every reason to see;
     * the schedule itself is not, which is why the bytes are gated separately below.
     */
    @GetMapping("/export-preview/{exportBatchId}/candidate-runs")
    public List<CandidateScheduleRunRecord> runsForExportBatch(
            @PathVariable UUID projectId,
            @PathVariable UUID exportBatchId,
            Actor actor
    ) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.runsForExportBatch(projectId, exportBatchId);
    }

    /** Every candidate calculation in the project, newest first. */
    @GetMapping("/candidate-runs")
    public List<CandidateScheduleRunRecord> runsForProject(@PathVariable UUID projectId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.runsForProject(projectId);
    }

    @GetMapping("/candidate-runs/{runId}")
    public CandidateScheduleRunRecord run(
            @PathVariable UUID projectId, @PathVariable UUID runId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.run(projectId, runId);
    }

    /**
     * The returned schedule itself.
     *
     * <p>Gated on {@code RETURN_CANDIDATE_SCHEDULE} rather than {@code VIEW_PROJECT}: this is a
     * complete recalculated Project schedule, and whoever may return one is who may read one back.
     *
     * <p>Served as an attachment under the name the planner uploaded, sanitized by
     * {@link ContentDisposition}, so a browser saves the file rather than trying to render a
     * schedule as a document.
     */
    @GetMapping("/candidate-runs/{runId}/content")
    public ResponseEntity<InputStreamResource> content(
            @PathVariable UUID projectId, @PathVariable UUID runId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.RETURN_CANDIDATE_SCHEDULE);

        CandidateScheduleRunService.CandidateScheduleContent content = service.content(projectId, runId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.run().candidateOriginalFilename())
                        .build()
                        .toString())
                .contentType(MediaType.APPLICATION_XML)
                .contentLength(content.run().candidateSizeBytes())
                .body(new InputStreamResource(content.content()));
    }
}
