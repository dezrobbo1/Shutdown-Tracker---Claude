package com.shutdowntracker.api.importbatch.handoff;

import com.shutdowntracker.api.actor.Actor;
import com.shutdowntracker.api.identity.Capability;
import com.shutdowntracker.api.identity.ProjectAuthorizationService;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/import-batches")
@ConditionalOnProperty(prefix = "shutdown-tracker.persistence", name = "enabled", havingValue = "true")
public class ImportBatchParseHandoffController {

    private final ImportBatchParseHandoffService service;
    private final ProjectAuthorizationService authorization;

    public ImportBatchParseHandoffController(ImportBatchParseHandoffService service, ProjectAuthorizationService authorization) {
        this.service = service;
        this.authorization = authorization;
    }

    @PostMapping("/{importBatchId}/request-parse-summary")
    public ImportBatchParseHandoffResponse requestParseSummary(
            @PathVariable UUID projectId,
            @PathVariable UUID importBatchId,
            Actor actor
    ) {
        authorization.requireCapability(projectId, actor, Capability.REQUEST_PROJECT_PARSE);
        return service.requestParseSummary(projectId, importBatchId);
    }
}
