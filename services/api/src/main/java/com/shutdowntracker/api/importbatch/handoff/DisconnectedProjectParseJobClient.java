package com.shutdowntracker.api.importbatch.handoff;

import com.shutdowntracker.projectimport.contract.ProjectParseEntitiesResponse;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        prefix = "shutdown-tracker.project-parse-worker",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class DisconnectedProjectParseJobClient implements ProjectParseJobClient {

    @Override
    public ProjectParseSummaryResponse requestParseSummary(ProjectParseSummaryRequest request) {
        Objects.requireNonNull(request, "request is required.");
        throw notConnected();
    }

    @Override
    public ProjectParseEntitiesResponse requestParseEntities(ProjectParseSummaryRequest request) {
        Objects.requireNonNull(request, "request is required.");
        throw notConnected();
    }

    private UnsupportedOperationException notConnected() {
        return new UnsupportedOperationException(
                "Project parse handoff is not connected. MPXJ parsing runs in services/project-worker; "
                        + "set shutdown-tracker.project-parse-worker.enabled=true and point base-url at it."
        );
    }
}
