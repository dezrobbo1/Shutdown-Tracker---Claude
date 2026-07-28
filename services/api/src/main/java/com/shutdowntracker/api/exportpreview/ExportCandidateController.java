package com.shutdowntracker.api.exportpreview;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/export-candidates")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ExportCandidateController {

    private final ExportCandidateService service;

    public ExportCandidateController(ExportCandidateService service) {
        this.service = service;
    }

    @PostMapping
    public ExportCandidateRecord createCandidate(
            @PathVariable UUID projectId,
            @RequestBody ExportCandidateCreateRequest request
    ) {
        return service.createCandidate(projectId, request);
    }

    @PostMapping("/{candidateId}/approval-events")
    public ExportCandidateApprovalEventRecord recordApprovalEvent(
            @PathVariable UUID projectId,
            @PathVariable UUID candidateId,
            @RequestBody ExportCandidateApprovalEventRequest request
    ) {
        return service.recordApprovalEvent(projectId, candidateId, request);
    }
}
