package com.shutdowntracker.api.importbatch;

import java.util.UUID;

public record ImportBatchRecord(
        UUID id,
        UUID projectId,
        UUID sourceFileId,
        ImportBatchStatus status,
        String parserName,
        String parserVersion,
        int warningCount,
        int errorCount
) {
}
