package com.shutdowntracker.projectworker.handoff;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactGenerationResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/worker/project-export")
public class WorkerProjectExportArtifactController {

    private final WorkerProjectExportArtifactHandoffService handoffService;

    public WorkerProjectExportArtifactController(WorkerProjectExportArtifactHandoffService handoffService) {
        this.handoffService = handoffService;
    }

    @PostMapping(
            value = "/generate-artifact",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ProjectExportArtifactGenerationResponse generateArtifact(
            @RequestBody ProjectExportArtifactGenerationRequest request
    ) {
        return handoffService.generateArtifact(request);
    }
}
