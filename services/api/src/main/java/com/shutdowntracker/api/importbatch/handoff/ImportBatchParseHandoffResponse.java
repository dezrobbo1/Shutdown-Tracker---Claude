package com.shutdowntracker.api.importbatch.handoff;

import com.shutdowntracker.api.importbatch.ImportBatchRecord;
import com.shutdowntracker.projectimport.contract.ProjectParseSummaryResponse;

public record ImportBatchParseHandoffResponse(
        ImportBatchRecord importBatch,
        ProjectParseSummaryResponse parseSummary,
        String message
) {

    public static ImportBatchParseHandoffResponse recorded(
            ImportBatchRecord importBatch,
            ProjectParseSummaryResponse parseSummary
    ) {
        return new ImportBatchParseHandoffResponse(
                importBatch,
                parseSummary,
                "Worker parse summary recorded on the import batch. No imported snapshot, task/resource/assignment rows, export artifact, schedule calculation, or Microsoft Project write-back was created."
        );
    }
}
