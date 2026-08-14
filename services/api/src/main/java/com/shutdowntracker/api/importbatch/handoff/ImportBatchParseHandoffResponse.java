package com.shutdowntracker.api.importbatch.handoff;

import com.shutdowntracker.api.importbatch.ImportBatchRecord;
import com.shutdowntracker.api.importedproject.ImportedProjectPersistenceResult;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;

public record ImportBatchParseHandoffResponse(
        ImportBatchRecord importBatch,
        ProjectParseSummaryResponse parseSummary,
        String message
) {

    public static ImportBatchParseHandoffResponse recorded(
            ImportBatchRecord importBatch,
            ProjectParseSummaryResponse parseSummary,
            ImportedProjectPersistenceResult persisted
    ) {
        return new ImportBatchParseHandoffResponse(
                importBatch,
                parseSummary,
                "Imported snapshot version %d stored with %d tasks, %d resources, %d assignments, and %d extended attributes. No export artifact, schedule calculation, or Microsoft Project write-back was created."
                        .formatted(
                                persisted.snapshot().snapshotVersion(),
                                persisted.taskCount(),
                                persisted.resourceCount(),
                                persisted.assignmentCount(),
                                persisted.extendedAttributeCount())
        );
    }
}
