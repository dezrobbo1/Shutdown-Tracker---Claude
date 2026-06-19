package com.shutdowntracker.projectworker.exporter;

import java.util.List;

public record ProjectExportArtifactRequest(
        String projectName,
        List<ProjectExportArtifactTask> tasks
) {
    public ProjectExportArtifactRequest {
        if (projectName == null || projectName.isBlank()) {
            throw new IllegalArgumentException("projectName is required.");
        }
        tasks = List.copyOf(tasks == null ? List.of() : tasks);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("At least one export task is required.");
        }
    }
}
