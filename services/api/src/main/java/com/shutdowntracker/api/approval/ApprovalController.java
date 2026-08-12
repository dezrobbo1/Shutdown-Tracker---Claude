package com.shutdowntracker.api.approval;

import com.shutdowntracker.api.actor.Actor;
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
@RequestMapping("/api/projects/{projectId}/approvals")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @PostMapping
    public ApprovalRecord recordDecision(
            @PathVariable UUID projectId,
            Actor actor,
            @RequestBody ApprovalRecordCreateRequest request
    ) {
        return service.recordDecision(projectId, actor, request);
    }

    @GetMapping
    public List<ApprovalRecord> listBySourceEntity(
            @PathVariable UUID projectId,
            @RequestParam String sourceEntityType,
            @RequestParam UUID sourceEntityId
    ) {
        return service.listBySourceEntity(projectId, sourceEntityType, sourceEntityId);
    }
}
