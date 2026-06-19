package com.shutdowntracker.projectworker.exporter;

import com.shutdowntracker.projectexport.contract.ProjectExportArtifactRequest;
import com.shutdowntracker.projectexport.contract.ProjectExportArtifactSummary;
import java.nio.file.Path;

public interface ProjectExportArtifactService {
    ProjectExportArtifactSummary generate(ProjectExportArtifactRequest request, Path outputPath);
}
