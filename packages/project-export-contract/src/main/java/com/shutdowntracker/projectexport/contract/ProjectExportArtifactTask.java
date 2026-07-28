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
        if (!importedTaskId.equals(importedTaskId.strip())) {
            throw new IllegalArgumentException("importedTaskId must not contain leading or trailing whitespace.");
        }
        microsoftProjectTaskUid = requireCanonicalPositiveInteger(
                microsoftProjectTaskUid,
                "microsoftProjectTaskUid"
        );
        microsoftProjectTaskId = requireCanonicalPositiveInteger(
                microsoftProjectTaskId,
                "microsoftProjectTaskId"
        );
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

    private static String requireCanonicalPositiveInteger(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(fieldName + " must be a canonical positive integer.");
        }
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + " must be a canonical positive integer.", exception);
        }
        return value;
    }
}
