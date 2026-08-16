package com.shutdowntracker.api.exportpreview;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Export lifecycle endpoints.
 *
 * <p>Every transition here is recorded as audit history, so the acting identity is taken from the
 * resolved {@link Actor} and the matching field on the request body is overwritten before the
 * service sees it. The body still carries the field because the persistence layer reads it, but a
 * caller cannot use it to attribute an approval, generation, open, or verification to someone else.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/export-preview")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ExportPreviewController {

    private final ExportPreviewService service;
    private final ProjectAuthorizationService authorization;

    public ExportPreviewController(ExportPreviewService service, ProjectAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @PostMapping
    public ExportPreviewDetail createPreview(
            @PathVariable UUID projectId,
            Actor actor,
            @RequestBody ExportPreviewCreateRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.CREATE_EXPORT_PREVIEW);
        return service.createPreview(projectId, request);
    }

    @GetMapping("/{exportBatchId}")
    public ExportPreviewDetail getPreview(
            @PathVariable UUID projectId,
            @PathVariable UUID exportBatchId
    ) {
        return service.getPreview(projectId, exportBatchId);
    }

    @PostMapping("/{exportBatchId}/approve")
    public ExportPreviewDetail approveBatch(
            @PathVariable UUID projectId,
            @PathVariable UUID exportBatchId,
            Actor actor,
            @RequestBody(required = false) ExportBatchDecisionRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.APPROVE_EXPORT_BATCH);
        return service.approveBatch(projectId, exportBatchId, reviewedBy(request, actor));
    }

    @PostMapping("/{exportBatchId}/reject")
    public ExportPreviewDetail rejectBatch(
            @PathVariable UUID projectId,
            @PathVariable UUID exportBatchId,
            Actor actor,
            @RequestBody(required = false) ExportBatchDecisionRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.APPROVE_EXPORT_BATCH);
        return service.rejectBatch(projectId, exportBatchId, reviewedBy(request, actor));
    }

    @PostMapping("/{exportBatchId}/mark-generated")
    public ExportPreviewDetail markGenerated(
            @PathVariable UUID projectId,
            @PathVariable UUID exportBatchId,
            Actor actor,
            @RequestBody ExportBatchGeneratedRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.GENERATE_EXPORT_ARTIFACT);
        return service.markGenerated(projectId, exportBatchId, generatedBy(request, actor));
    }

    @PostMapping("/{exportBatchId}/mark-opened-in-microsoft-project")
    public ExportPreviewDetail markOpenedInMicrosoftProject(
            @PathVariable UUID projectId,
            @PathVariable UUID exportBatchId,
            Actor actor,
            @RequestBody ExportBatchProjectOpenRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.RECORD_EXPORT_VERIFICATION);
        return service.markOpenedInMicrosoftProject(projectId, exportBatchId, openedBy(request, actor));
    }

    @PostMapping("/{exportBatchId}/verify")
    public ExportPreviewDetail verifyBatch(
            @PathVariable UUID projectId,
            @PathVariable UUID exportBatchId,
            Actor actor,
            @RequestBody ExportBatchVerificationRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.RECORD_EXPORT_VERIFICATION);
        return service.verifyBatch(projectId, exportBatchId, verifiedBy(request, actor));
    }

    private static ExportBatchDecisionRequest reviewedBy(ExportBatchDecisionRequest request, Actor actor) {
        ExportBatchDecisionRequest source = request == null ? ExportBatchDecisionRequest.empty() : request;
        return new ExportBatchDecisionRequest(actor.userId(), source.reason(), source.metadata());
    }

    private static ExportBatchGeneratedRequest generatedBy(ExportBatchGeneratedRequest request, Actor actor) {
        return new ExportBatchGeneratedRequest(
                request.exportFileUri(),
                request.exportFileHash(),
                actor.userId(),
                request.reason(),
                request.clientMetadata(),
                request.provenance()
        );
    }

    private static ExportBatchProjectOpenRequest openedBy(ExportBatchProjectOpenRequest request, Actor actor) {
        return new ExportBatchProjectOpenRequest(actor.userId(), request.reason(), request.metadata());
    }

    private static ExportBatchVerificationRequest verifiedBy(ExportBatchVerificationRequest request, Actor actor) {
        return new ExportBatchVerificationRequest(actor.userId(), request.reason(), request.metadata());
    }
}
