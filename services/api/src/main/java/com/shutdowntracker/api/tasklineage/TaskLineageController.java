package com.shutdowntracker.api.tasklineage;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/import-review/lineage-links")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class TaskLineageController {

    private final TaskLineageService service;
    private final ProjectAuthorizationService authorization;

    public TaskLineageController(TaskLineageService service, ProjectAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping
    public List<TaskLineageRecord> listBySnapshotPair(
            @PathVariable UUID projectId,
            @RequestParam UUID previousSnapshotId,
            @RequestParam UUID currentSnapshotId,
            Actor actor
    ) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.listBySnapshotPair(projectId, previousSnapshotId, currentSnapshotId);
    }

    @PostMapping
    public TaskLineageRecord createSuggested(
            @PathVariable UUID projectId,
            Actor actor,
            @RequestBody TaskLineageCreateRequest request
    ) {
        authorization.requireCapability(projectId, actor, Capability.RECONCILE_TASK_LINEAGE);
        return service.createSuggested(projectId, request, actor);
    }

    @PostMapping("/{lineageLinkId}/accept")
    public TaskLineageDecisionResponse accept(
            @PathVariable UUID projectId,
            @PathVariable UUID lineageLinkId,
            Actor actor
    ) {
        authorization.requireCapability(projectId, actor, Capability.RECONCILE_TASK_LINEAGE);
        return service.accept(projectId, lineageLinkId, actor);
    }

    @PostMapping("/{lineageLinkId}/reject")
    public TaskLineageDecisionResponse reject(
            @PathVariable UUID projectId,
            @PathVariable UUID lineageLinkId,
            Actor actor
    ) {
        authorization.requireCapability(projectId, actor, Capability.RECONCILE_TASK_LINEAGE);
        return service.reject(projectId, lineageLinkId, actor);
    }
}
