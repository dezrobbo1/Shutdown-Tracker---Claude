package com.shutdowntracker.api.criticalwatch;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CriticalWatchRepository {

    CriticalWatchlistRecord createWatchlist(UUID projectId, String name, String description, UUID createdByUserId);

    List<CriticalWatchlistRecord> findActiveWatchlists(UUID projectId);

    CriticalWorkPackageRecord createWorkPackage(
            UUID projectId, UUID watchlistId, String name, String description, UUID createdByUserId);

    Optional<CriticalWorkPackageRecord> findWorkPackage(UUID projectId, UUID workPackageId);

    /**
     * The active packages on a watchlist.
     *
     * <p>Scoped by project as well as watchlist: a watchlist id is not a secret, and without
     * the project filter a caller authorised on one project could read another project's
     * packages by naming its watchlist.
     */
    List<CriticalWorkPackageRecord> findWorkPackages(UUID projectId, UUID watchlistId);

    CriticalWorkPackageSourceRecord addSource(
            UUID projectId,
            UUID workPackageId,
            UUID projectSnapshotId,
            UUID importedTaskId,
            String sourceType,
            boolean includeDescendants,
            UUID createdByUserId);

    List<CriticalWorkPackageSourceRecord> findSources(UUID workPackageId);

    /** The tasks a package reports on, expanded from its summary-task sources. */
    List<UUID> findReportedTaskIds(UUID workPackageId);

    /** Reporting coverage for every active package on an active watchlist in this project. */
    List<CriticalWorkPackageReportingSummary> findReportingSummaries(UUID projectId);

    Optional<CriticalUpdateRecord> findUpdateByIdempotencyKey(UUID projectId, String idempotencyKey);

    CriticalUpdateRecord submitUpdate(UUID projectId, UUID submittedByUserId, CriticalUpdateSubmitRequest request);

    /**
     * Marks an earlier update as superseded by a correction.
     *
     * <p>Scoped to the project and the package being reported on, so a correction can only
     * retire a report on the same work. Returns the number of rows changed; zero means the
     * update named does not belong there, which the caller must treat as a refusal rather
     * than as success.
     */
    int markUpdateSuperseded(UUID projectId, UUID workPackageId, UUID criticalUpdateId);

    List<CriticalUpdateRecord> findUpdates(UUID workPackageId);

    int countUpdateLines(UUID criticalUpdateId);
}
