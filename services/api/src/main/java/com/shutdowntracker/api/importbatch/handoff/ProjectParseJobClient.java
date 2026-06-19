package com.shutdowntracker.api.importbatch.handoff;

import com.shutdowntracker.projectimport.contract.ProjectParseSummaryRequest;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;

public interface ProjectParseJobClient {

    ProjectParseSummaryResponse requestParseSummary(ProjectParseSummaryRequest request);
}
