package com.shutdowntracker.api.importreview;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/import-review")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ImportReviewController {

    private final ImportReviewService service;
    private final ProjectAuthorizationService authorization;

    public ImportReviewController(ImportReviewService service, ProjectAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @GetMapping("/snapshots")
    public List<ImportReviewSnapshotSummary> listSnapshots(@PathVariable UUID projectId, Actor actor) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.listSnapshots(projectId);
    }

    @GetMapping("/snapshots/{snapshotId}")
    public ImportReviewSnapshotDetail getSnapshot(
            @PathVariable UUID projectId,
            @PathVariable UUID snapshotId,
            Actor actor
    ) {
        authorization.requireCapability(projectId, actor, Capability.VIEW_PROJECT);
        return service.getSnapshot(projectId, snapshotId);
    }

    @PostMapping("/snapshots/{snapshotId}/accept")
    public ImportReviewDecisionResponse acceptSnapshot(
            @PathVariable UUID projectId,
            @PathVariable UUID snapshotId,
            Actor actor
    ) {
        authorization.requireCapability(projectId, actor, Capability.ACCEPT_IMPORT_SNAPSHOT);
        return service.acceptSnapshot(projectId, snapshotId, actor);
    }

    @PostMapping("/snapshots/{snapshotId}/reject")
    public ImportReviewDecisionResponse rejectSnapshot(
            @PathVariable UUID projectId,
            @PathVariable UUID snapshotId,
            Actor actor
    ) {
        authorization.requireCapability(projectId, actor, Capability.REJECT_IMPORT_SNAPSHOT);
        return service.rejectSnapshot(projectId, snapshotId, actor);
    }
}
