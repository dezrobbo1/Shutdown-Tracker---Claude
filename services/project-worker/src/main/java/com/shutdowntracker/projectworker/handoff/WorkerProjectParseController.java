package com.shutdowntracker.projectworker.handoff;

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
}
