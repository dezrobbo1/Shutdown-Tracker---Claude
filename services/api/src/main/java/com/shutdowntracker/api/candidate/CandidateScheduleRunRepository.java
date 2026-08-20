package com.shutdowntracker.api.candidate;

import com.shutdowntracker.api.exportpreview.ExportBatchState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateScheduleRunRepository {

    /**
     * The export batch a candidate is being returned against, with what it handed Microsoft
     * Project and which accepted snapshot it was built from.
     */
    Optional<ExportBatchForReturn> findExportBatch(UUID projectId, UUID exportBatchId);

    /** The accepted source file behind a snapshot, and the hash recorded for it at import. */
    Optional<AcceptedSource> findAcceptedSource(UUID projectId, UUID projectSnapshotId);

    /** An existing run for the same batch and the same bytes, which a re-upload resolves to. */
    Optional<CandidateScheduleRunRecord> findByContentHash(
            UUID projectId, UUID exportBatchId, String candidateContentHash);

    CandidateScheduleRunRecord create(NewCandidateScheduleRun run);

    List<CandidateScheduleRunRecord> findForExportBatch(UUID projectId, UUID exportBatchId);

    List<CandidateScheduleRunRecord> findForProject(UUID projectId);

    Optional<CandidateScheduleRunRecord> find(UUID projectId, UUID runId);

    /**
     * Where a run's returned file is kept.
     *
     * <p>Deliberately not a field on {@link CandidateScheduleRunRecord}: the record is serialized to
     * clients, and a storage path is not something a client has any use for.
     */
    Optional<String> findStorageUri(UUID projectId, UUID runId);

    record ExportBatchForReturn(
            UUID exportBatchId,
            UUID projectSnapshotId,
            ExportBatchState state,
            String exportFileHash
    ) {
    }

    record AcceptedSource(UUID sourceFileId, String contentHash) {
    }

    record NewCandidateScheduleRun(
            UUID projectId,
            UUID exportBatchId,
            UUID projectSnapshotId,
            UUID acceptedSourceFileId,
            String acceptedSourceFileHash,
            String generatedArtifactHash,
            String candidateOriginalFilename,
            String candidateStorageUri,
            String candidateContentHash,
            long candidateSizeBytes,
            String microsoftProjectVersion,
            String plannerNote,
            UUID returnedByUserId
    ) {
    }
}
