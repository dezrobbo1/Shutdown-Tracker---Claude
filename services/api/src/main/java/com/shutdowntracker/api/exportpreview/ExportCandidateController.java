package com.shutdowntracker.api.exportpreview;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authoritative export candidates and the approval events bound to them.
 *
 * <p>This is the first step of the export authority chain, so it is authorised the same way as the
 * batch lifecycle that follows it. An approval event is recorded audit history: the reviewing
 * identity comes from the resolved {@link Actor} and the matching field on the request body is
 * overwritten before the service sees it, so a caller cannot attribute an approval to someone else.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/export-candidates")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ExportCandidateController {

    private final ExportCandidateService service;
    private final ProjectAuthorizationService authorization;

    public ExportCandidateController(ExportCandidateService service, ProjectAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @PostMapping
    public ExportCandidateRecord createCandidate(
            @PathVariable UUID projectId,
            Actor actor,
            @RequestBody ExportCandidateCreateRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.CREATE_EXPORT_PREVIEW);
        return service.createCandidate(projectId, request);
    }

    @PostMapping("/{candidateId}/approval-events")
    public ExportCandidateApprovalEventRecord recordApprovalEvent(
            @PathVariable UUID projectId,
            @PathVariable UUID candidateId,
            Actor actor,
            @RequestBody ExportCandidateApprovalEventRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.RECORD_APPROVAL);
        return service.recordApprovalEvent(projectId, candidateId, reviewedBy(request, actor));
    }

    private static ExportCandidateApprovalEventRequest reviewedBy(
            ExportCandidateApprovalEventRequest request,
            Actor actor
    ) {
        return new ExportCandidateApprovalEventRequest(
                request.approvalState(),
                request.requestedAt(),
                actor.userId(),
                request.reviewedAt(),
                request.reason(),
                request.metadata()
        );
    }
}
