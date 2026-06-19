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
        fieldValues = List.copyOf(fieldValues == null ? List.of() : fieldValues);
        if (fieldValues.isEmpty()) {
            throw new IllegalArgumentException("At least one field value is required for each export task.");
        }
    }
}
