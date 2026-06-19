package com.shutdowntracker.api.tasklineage;

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

    public TaskLineageController(TaskLineageService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaskLineageRecord> listBySnapshotPair(
            @PathVariable UUID projectId,
            @RequestParam UUID previousSnapshotId,
            @RequestParam UUID currentSnapshotId
    ) {
        return service.listBySnapshotPair(projectId, previousSnapshotId, currentSnapshotId);
    }

    @PostMapping
    public TaskLineageRecord createSuggested(
            @PathVariable UUID projectId,
            @RequestBody TaskLineageCreateRequest request
    ) {
        return service.createSuggested(projectId, request);
    }

    @PostMapping("/{lineageLinkId}/accept")
    public TaskLineageDecisionResponse accept(
            @PathVariable UUID projectId,
            @PathVariable UUID lineageLinkId
    ) {
        return service.accept(projectId, lineageLinkId);
    }

    @PostMapping("/{lineageLinkId}/reject")
    public TaskLineageDecisionResponse reject(
            @PathVariable UUID projectId,
            @PathVariable UUID lineageLinkId
    ) {
        return service.reject(projectId, lineageLinkId);
    }
}
