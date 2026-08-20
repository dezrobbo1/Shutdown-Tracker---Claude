package com.shutdowntracker.api.candidate.storage;

/**
 * A returned candidate schedule as it was actually stored.
 *
 * <p>{@code contentHashSha256} is computed from the bytes that reached the store, never taken from
 * the caller. The hash is what a planner decision is later bound to, so it has to be a fact about
 * the file rather than a claim about it.
 */
public record StoredCandidateSchedule(
        String storageUri,
        String originalFilename,
        String storedFilename,
        long sizeBytes,
        String contentHashSha256
) {
}
