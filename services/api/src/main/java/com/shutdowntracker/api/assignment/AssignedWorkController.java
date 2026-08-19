package com.shutdowntracker.api.assignment;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class AssignedWorkController {

    private final AssignedWorkService service;
    private final ProjectAuthorizationService authorization;

    public AssignedWorkController(AssignedWorkService service, ProjectAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    /**
     * The acting user's own assigned work.
     *
     * <p>{@code VIEW_PROJECT} is the whole check. Reading your own work list needs no more authority
     * than seeing the project, and the endpoint takes no user parameter, so there is nothing to
     * escalate: the only work list it can return is the caller's.
     */
    @GetMapping("/assigned-work")
    public AssignedWorkView assignedWork(@PathVariable UUID projectId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.assignedWork(projectId, actor);
    }

    /**
     * Every resource link in the project.
     *
     * <p>Readable by anyone who can view the project, in the same spirit as the console's sections:
     * knowing who is linked to which resource is useful to a supervisor working out why somebody's
     * work list is empty, and it is not sensitive. Changing one is not.
     */
    @GetMapping("/resource-links")
    public List<ProjectResourceLinkRecord> links(@PathVariable UUID projectId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.links(projectId);
    }

    /**
     * Users and resources a link can be made between.
     *
     * <p>Gated on {@code MANAGE_RESOURCE_LINK} rather than {@code VIEW_PROJECT}: the existing links
     * are ordinary project information, but the list of everyone on the project is only needed by
     * somebody about to link one of them.
     */
    @GetMapping("/resource-links/candidates")
    public AssignedWorkService.LinkCandidates candidates(@PathVariable UUID projectId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.MANAGE_RESOURCE_LINK);
        return service.candidates(projectId);
    }

    @PostMapping("/resource-links")
    public ProjectResourceLinkRecord link(
            @PathVariable UUID projectId,
            Actor actor,
            @RequestBody ProjectResourceLinkCreateRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.MANAGE_RESOURCE_LINK);
        return service.link(projectId, actor, request);
    }

    /**
     * Revokes a link.
     *
     * <p>A POST to {@code /revoke} rather than a DELETE, because nothing is deleted: the row stays
     * and changes state, so the audit trail keeps who linked whom and who undid it. The verb matches
     * the surrounding endpoints — snapshots are accepted, problems are closed — rather than implying
     * the record is gone.
     */
    @PostMapping("/resource-links/{linkId}/revoke")
    public ProjectResourceLinkRecord revoke(
            @PathVariable UUID projectId,
            @PathVariable UUID linkId,
            Actor actor
    ) {
        authorization.requireCapability(projectId, actor, Capability.MANAGE_RESOURCE_LINK);
        return service.revoke(projectId, actor, linkId);
    }
}
