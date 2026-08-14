package com.shutdowntracker.api.importbatch.handoff;

import com.shutdowntracker.projectimport.contract.ProjectParseEntitiesResponse;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;

public interface ProjectParseJobClient {

    ProjectParseSummaryResponse requestParseSummary(ProjectParseSummaryRequest request);

    /**
     * Requests the parsed entities as well as the counts. Used by the import path so a
     * snapshot can actually be stored; {@link #requestParseSummary} remains available for
     * callers that only need to report on a file.
     */
    ProjectParseEntitiesResponse requestParseEntities(ProjectParseSummaryRequest request);
}
