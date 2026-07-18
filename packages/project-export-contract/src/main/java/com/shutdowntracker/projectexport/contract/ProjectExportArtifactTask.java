package com.shutdowntracker.projectexport.contract;

import java.util.List;

public record ProjectExportArtifactTask(
        String importedTaskId,
        String microsoftProjectTaskUid,
        String microsoftProjectTaskId,
        String taskName,
        boolean leafTask,
        List<ProjectExportArtifactFieldValue> fieldValues
) {
    public ProjectExportArtifactTask {
        if (importedTaskId == null || importedTaskId.isBlank()) {
            throw new IllegalArgumentException("importedTaskId is required.");
        }
        if (taskName == null || taskName.isBlank()) {
            throw new IllegalArgumentException("taskName is required.");
        }
        if (!leafTask) {
            throw new IllegalArgumentException("Only leaf-task export candidates are allowed by the worker contract.");
        }
        fieldValues = List.copyOf(fieldValues == null ? List.of() : fieldValues);
        if (fieldValues.isEmpty()) {
            throw new IllegalArgumentException("At least one field value is required for each export task.");
        }
    }
}
