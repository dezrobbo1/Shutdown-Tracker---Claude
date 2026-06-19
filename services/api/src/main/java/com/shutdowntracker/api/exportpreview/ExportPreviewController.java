package com.shutdowntracker.api.exportpreview;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/export-preview")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ExportPreviewController {

    private final ExportPreviewService service;

    public ExportPreviewController(ExportPreviewService service) {
        this.service = service;
    }

    @PostMapping
    public ExportPreviewDetail createPreview(
            @PathVariable UUID projectId,
            @RequestBody ExportPreviewCreateRequest request
    ) {
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
            @RequestBody(required = false) ExportBatchDecisionRequest request
    ) {
        return service.approveBatch(projectId, exportBatchId, request);
    }

    @PostMapping("/{exportBatchId}/reject")
    public ExportPreviewDetail rejectBatch(
            @PathVariable UUID projectId,
            @PathVariable UUID exportBatchId,
            @RequestBody(required = false) ExportBatchDecisionRequest request
    ) {
        return service.rejectBatch(projectId, exportBatchId, request);
    }

    @PostMapping("/{exportBatchId}/mark-generated")
    public ExportPreviewDetail markGenerated(
            @PathVariable UUID projectId,
            @PathVariable UUID exportBatchId,
            @RequestBody ExportBatchGeneratedRequest request
    ) {
        return service.markGenerated(projectId, exportBatchId, request);
    }
}
