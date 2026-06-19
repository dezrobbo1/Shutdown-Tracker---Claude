package com.shutdowntracker.api.importbatch.handoff;

import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class DisconnectedProjectParseJobClient implements ProjectParseJobClient {

    @Override
    public ProjectParseSummaryResponse requestParseSummary(ProjectParseSummaryRequest request) {
        Objects.requireNonNull(request, "request is required.");
        throw new UnsupportedOperationException(
                "Project parse handoff is not connected yet. MPXJ parsing remains in services/project-worker; "
                        + "the API does not parse files, persist parser output, or create worker jobs."
        );
    }
}
