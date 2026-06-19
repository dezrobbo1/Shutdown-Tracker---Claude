package com.shutdowntracker.projectworker.exporter;

import java.nio.file.Path;

public interface ProjectExportArtifactService {
    ProjectExportArtifactSummary generate(ProjectExportArtifactRequest request, Path outputPath);
}
