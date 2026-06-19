package com.shutdowntracker.api.importedproject;

public record ImportedProjectPersistenceResult(
        ProjectSnapshotRecord snapshot,
        int taskCount,
        int resourceCount,
        int assignmentCount,
        int extendedAttributeCount
) {
    public ImportedProjectPersistenceResult {
        snapshot = ImportedProjectRecordValidation.requireNonNull(snapshot, "snapshot is required.");
        ImportedProjectRecordValidation.requireNonNegative(taskCount, "taskCount");
        ImportedProjectRecordValidation.requireNonNegative(resourceCount, "resourceCount");
        ImportedProjectRecordValidation.requireNonNegative(assignmentCount, "assignmentCount");
        ImportedProjectRecordValidation.requireNonNegative(extendedAttributeCount, "extendedAttributeCount");
    }
}
