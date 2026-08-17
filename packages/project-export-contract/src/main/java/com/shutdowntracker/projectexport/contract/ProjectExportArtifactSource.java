package com.shutdowntracker.projectexport.contract;

import java.util.Objects;
import java.util.UUID;

/**
 * The accepted source schedule a candidate is derived from.
 *
 * <p>A candidate schedule is the accepted source with the approved execution inputs applied to it,
 * so the worker has to read the original uploaded file rather than rebuild a schedule from stored
 * rows. The imported snapshot in the database is a read-and-report projection: it holds no task
 * dependencies, calendars, constraints, baselines, or typed durations, and cannot reconstruct a
 * schedule Microsoft Project could recalculate.
 *
 * <p>{@code contentHash} is the hash recorded when the file was uploaded. The worker verifies the
 * bytes against it before parsing, so a candidate can never be built from a file that changed
 * underneath the accepted snapshot.
 */
public record ProjectExportArtifactSource(
        UUID sourceFileId,
        String storageUri,
        String contentHash
) {
    public ProjectExportArtifactSource {
        Objects.requireNonNull(sourceFileId, "sourceFileId is required.");
        storageUri = requireText(storageUri, "storageUri is required.");
        contentHash = requireText(contentHash, "contentHash is required.");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
