package com.shutdowntracker.api.importbatch.handoff;

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

    public ImportBatchParseHandoffController(ImportBatchParseHandoffService service) {
        this.service = service;
    }

    @PostMapping("/{importBatchId}/request-parse-summary")
    public ImportBatchParseHandoffResponse requestParseSummary(
            @PathVariable UUID projectId,
            @PathVariable UUID importBatchId
    ) {
        return service.requestParseSummary(projectId, importBatchId);
    }
}
