package com.shutdowntracker.api.exportpreview.handoff;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generating the export artifact.
 *
 * <p>This shares a base path with {@code ExportPreviewController} and writes the same generation
 * facts, so it is authorised the same way. Generation is recorded as audit history: the generating
 * identity comes from the resolved {@link Actor} and the matching field on the request body is
 * overwritten before the service sees it, so a caller cannot attribute an artifact to someone else.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/export-preview")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ExportArtifactHandoffController {

    private final ExportArtifactHandoffService service;
    private final ProjectAuthorizationService authorization;

    public ExportArtifactHandoffController(
            ExportArtifactHandoffService service,
            ProjectAuthorizationService authorization
    ) {
        this.service = service;
        this.authorization = authorization;
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
        authorization.requireCapability(projectId, actor, Capability.GENERATE_EXPORT_ARTIFACT);
        return service.generateArtifact(projectId, exportBatchId, generatedBy(request, actor));
    }

    private static ExportArtifactGenerationRequest generatedBy(
            ExportArtifactGenerationRequest request,
            Actor actor
    ) {
        ExportArtifactGenerationRequest source =
                request == null ? ExportArtifactGenerationRequest.empty() : request;
        return new ExportArtifactGenerationRequest(actor.userId(), source.reason(), source.metadata());
    }
}
