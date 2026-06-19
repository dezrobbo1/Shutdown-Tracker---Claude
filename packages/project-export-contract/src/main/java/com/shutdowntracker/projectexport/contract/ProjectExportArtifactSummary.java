package com.shutdowntracker.projectexport.contract;

import java.util.List;

public record ProjectExportArtifactSummary(
        String outputFilename,
        String artifactFormat,
        int taskCount,
        int exportedFieldCount,
        long sizeBytes,
        String sha256,
        List<String> notes
) {
    public ProjectExportArtifactSummary {
        notes = List.copyOf(notes == null ? List.of() : notes);
    }
}
