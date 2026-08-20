package com.shutdowntracker.api.candidate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One candidate schedule a planner brought back from Microsoft Project.
 *
 * <p>Three identities travel together on purpose. {@code acceptedSourceFileHash} is the schedule
 * the candidate had to be derived from, {@code generatedArtifactHash} is what Shutdown Tracker
 * handed Microsoft Project, and {@code candidateContentHash} is what came back. A review that
 * cannot show all three cannot say what it compared.
 *
 * <p>{@code candidateStorageUri} is not exposed: where the file is kept is the store's business,
 * and the console reads the bytes back through the API rather than by naming a path.
 */
public record CandidateScheduleRunRecord(
        UUID id,
        UUID projectId,
        UUID exportBatchId,
        UUID projectSnapshotId,
        UUID acceptedSourceFileId,
        String acceptedSourceFileHash,
        String generatedArtifactHash,
        CandidateScheduleRunState state,
        String candidateOriginalFilename,
        String candidateContentHash,
        long candidateSizeBytes,
        String microsoftProjectVersion,
        String plannerNote,
        OffsetDateTime returnedAt,
        UUID returnedByUserId,
        String returnedByDisplayName,
        UUID supersededByCandidateScheduleRunId
) {
}
