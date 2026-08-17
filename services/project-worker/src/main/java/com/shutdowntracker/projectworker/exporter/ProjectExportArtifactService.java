package com.shutdowntracker.projectworker.exporter;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSummary;
import java.nio.file.Path;

public interface ProjectExportArtifactService {

    /**
     * Produces a candidate schedule: the accepted source schedule with the approved execution
     * inputs applied to it.
     *
     * @param sourcePath the accepted source schedule, opened read-only and never written back
     */
    ProjectExportArtifactSummary generate(
            ProjectExportArtifactRequest request,
            Path sourcePath,
            Path outputPath
    );
}
