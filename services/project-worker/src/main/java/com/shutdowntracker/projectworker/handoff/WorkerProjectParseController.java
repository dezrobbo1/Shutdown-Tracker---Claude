package com.shutdowntracker.projectworker.handoff;

import com.shutdowntracker.projectimport.contract.ProjectParseEntitiesResponse;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/worker/project-import")
public class WorkerProjectParseController {

    private final WorkerProjectParseHandoffService handoffService;

    public WorkerProjectParseController(WorkerProjectParseHandoffService handoffService) {
        this.handoffService = handoffService;
    }

    @PostMapping(
            value = "/parse-summary",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ProjectParseSummaryResponse summarize(@RequestBody ProjectParseSummaryRequest request) {
        return handoffService.summarize(request);
    }

    /**
     * Returns the parsed entities as well as the counts. Responses are large — a few
     * thousand tasks for a real shutdown schedule — so callers must allow for a body
     * measured in megabytes rather than kilobytes.
     */
    @PostMapping(
            value = "/parse-entities",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ProjectParseEntitiesResponse parseEntities(@RequestBody ProjectParseSummaryRequest request) {
        return handoffService.parseEntities(request);
    }
}
