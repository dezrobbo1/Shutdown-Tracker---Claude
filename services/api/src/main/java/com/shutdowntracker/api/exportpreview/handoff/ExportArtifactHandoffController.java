package com.shutdowntracker.api.exportpreview.handoff;

import com.shutdowntracker.api.actor.Actor;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/export-preview")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ExportArtifactHandoffController {

    private final ExportArtifactHandoffService service;

    public ExportArtifactHandoffController(ExportArtifactHandoffService service) {
        this.service = service;
    }

    @PostMapping(
            value = "/{exportBatchId}/generate-artifact",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ExportArtifactGenerationResponse generateArtifact(
            @PathVariable UUID projectId,
            @PathVariable UUID exportBatchId,
            Actor actor,
            @RequestBody(required = false) ExportArtifactGenerationRequest request
    ) {
        return service.generateArtifact(projectId, exportBatchId, actor, request);
    }
}
