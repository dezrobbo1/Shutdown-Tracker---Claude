package com.shutdowntracker.api.importreview;

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

    public ImportReviewController(ImportReviewService service) {
        this.service = service;
    }

    @GetMapping("/snapshots")
    public List<ImportReviewSnapshotSummary> listSnapshots(@PathVariable UUID projectId) {
        return service.listSnapshots(projectId);
    }

    @GetMapping("/snapshots/{snapshotId}")
    public ImportReviewSnapshotDetail getSnapshot(
            @PathVariable UUID projectId,
            @PathVariable UUID snapshotId
    ) {
        return service.getSnapshot(projectId, snapshotId);
    }

    @PostMapping("/snapshots/{snapshotId}/accept")
    public ImportReviewDecisionResponse acceptSnapshot(
            @PathVariable UUID projectId,
            @PathVariable UUID snapshotId
    ) {
        return service.acceptSnapshot(projectId, snapshotId);
    }

    @PostMapping("/snapshots/{snapshotId}/reject")
    public ImportReviewDecisionResponse rejectSnapshot(
            @PathVariable UUID projectId,
            @PathVariable UUID snapshotId
    ) {
        return service.rejectSnapshot(projectId, snapshotId);
    }
}
