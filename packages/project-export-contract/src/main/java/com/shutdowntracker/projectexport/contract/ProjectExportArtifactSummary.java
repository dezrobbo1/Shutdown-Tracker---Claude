package com.shutdowntracker.projectexport.contract;

import java.util.List;

/**
 * What was generated.
 *
 * <p>{@code taskCount} counts the tasks Shutdown Tracker <em>updated</em>; {@code sourceTaskCount}
 * counts every task carried through from the accepted source. The two are deliberately separate:
 * a candidate schedule contains the whole schedule, so a single number would silently answer a
 * different question depending on which one a reader assumed.
 */
public record ProjectExportArtifactSummary(
        String outputFilename,
        String artifactFormat,
        int taskCount,
        int sourceTaskCount,
        int exportedFieldCount,
        long sizeBytes,
        String sha256,
        List<String> notes
) {
    public ProjectExportArtifactSummary {
        notes = List.copyOf(notes == null ? List.of() : notes);
    }
}
